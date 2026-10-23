package com.videocloud.video;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
@RestController @RequestMapping("/api/v1/public/videos")
public class PublicVideoController {
 public record PublicVideo(UUID id,String title,String description,String status,String playbackPath){}
 private static final Pattern ASSET=Pattern.compile("(index\\.m3u8|thumbnail\\.jpg|segment-[0-9]{5}\\.ts)");
 private final JdbcTemplate jdbc;private final Path root;
 public PublicVideoController(JdbcTemplate jdbc,@Value("${videocloud.media-root:./var/media}")String mediaRoot){this.jdbc=jdbc;this.root=Path.of(mediaRoot).toAbsolutePath().normalize();}
 @GetMapping("/{id}")public PublicVideo get(@PathVariable UUID id){var rows=jdbc.query("SELECT id,title,description,status FROM videos WHERE id=? AND visibility='PUBLIC' AND status='READY' AND deleted_at IS NULL",(rs,n)->new PublicVideo(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("description"),rs.getString("status"),"/api/v1/public/videos/"+id+"/assets/index.m3u8"),id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Public video not found");return rows.get(0);}
 @GetMapping("/{id}/assets/{asset}")public ResponseEntity<Resource> asset(@PathVariable UUID id,@PathVariable String asset){if(!ASSET.matcher(asset).matches())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found");var keys=jdbc.query("SELECT source_object_key FROM videos WHERE id=? AND visibility='PUBLIC' AND status='READY' AND deleted_at IS NULL",(rs,n)->rs.getString(1),id);if(keys.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Public video not found");Path source=root.resolve(keys.get(0)).normalize();Path dir=source.getParent(),file=dir.resolve(asset).normalize();if(!file.startsWith(dir)||!Files.isRegularFile(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found");MediaType type=asset.endsWith(".m3u8")?MediaType.parseMediaType("application/vnd.apple.mpegurl"):asset.endsWith(".ts")?MediaType.parseMediaType("video/mp2t"):MediaType.IMAGE_JPEG;return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public, max-age=60").contentType(type).body(new FileSystemResource(file));}
}
