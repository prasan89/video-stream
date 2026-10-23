package com.videocloud.video;
import com.videocloud.api.CurrentWorkspace;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
@RestController
public class ShareController {
 public record CreateShareResponse(String shareId,String token,String url,String note){}
 public record SharedVideo(UUID id,String title,String description,String status,String playbackPath){}
 private static final Pattern ASSET=Pattern.compile("(index\\.m3u8|thumbnail\\.jpg|segment-[0-9]{5}\\.ts)");
 private final JdbcTemplate jdbc;private final CurrentWorkspace workspaces;private final Path root;
 public ShareController(JdbcTemplate jdbc,CurrentWorkspace workspaces,@org.springframework.beans.factory.annotation.Value("${videocloud.media-root:./var/media}")String mediaRoot){this.jdbc=jdbc;this.workspaces=workspaces;this.root=Path.of(mediaRoot).toAbsolutePath().normalize();}
 @PostMapping("/api/v1/videos/{id}/shares") @ResponseStatus(HttpStatus.CREATED)
 public CreateShareResponse create(@PathVariable UUID id,Authentication auth){
  UUID ws=workspaces.id(auth),user=workspaces.userId(auth);var found=jdbc.query("SELECT id FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",(rs,n)->rs.getObject(1,UUID.class),id,ws);if(found.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);String hash=sha256(token);UUID shareId=jdbc.queryForObject("INSERT INTO video_shares(video_id,token_hash,created_by) VALUES (?,?,?) RETURNING id",UUID.class,id,hash,user);
  return new CreateShareResponse(shareId.toString(),token,"/api/v1/shared/"+token,"Keep this link private. Anyone with the link can access this video until revoked.");
 }
 @DeleteMapping("/api/v1/videos/{id}/shares/{shareId}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revoke(@PathVariable UUID id,@PathVariable UUID shareId,Authentication auth){int n=jdbc.update("UPDATE video_shares s SET revoked_at=now() FROM videos v WHERE s.id=? AND s.video_id=v.id AND v.id=? AND v.workspace_id=? AND s.revoked_at IS NULL",shareId,id,workspaces.id(auth));if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Share not found");}
 @GetMapping("/api/v1/shared/{token}")
 public SharedVideo resolve(@PathVariable String token){var rows=jdbc.query("SELECT v.id,v.title,v.description,v.status FROM video_shares s JOIN videos v ON v.id=s.video_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND (s.expires_at IS NULL OR s.expires_at>now()) AND v.deleted_at IS NULL",(rs,n)->new SharedVideo(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("description"),rs.getString("status"),"/api/v1/shared/"+token+"/assets/index.m3u8"),sha256(token));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Share link is invalid or revoked");return rows.get(0);}
 @GetMapping("/api/v1/shared/{token}/assets/{asset}")
 public ResponseEntity<Resource> asset(@PathVariable String token,@PathVariable String asset){if(!ASSET.matcher(asset).matches())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found");var ids=jdbc.query("SELECT v.id FROM video_shares s JOIN videos v ON v.id=s.video_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND (s.expires_at IS NULL OR s.expires_at>now()) AND v.deleted_at IS NULL AND v.status='READY'",(rs,n)->rs.getObject(1,UUID.class),sha256(token));if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Share link invalid or video not ready");Path dir=root.resolve(ids.get(0).toString()).normalize(),file=dir.resolve(asset).normalize();if(!file.startsWith(dir)||!Files.isRegularFile(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found");MediaType type=asset.endsWith(".m3u8")?MediaType.parseMediaType("application/vnd.apple.mpegurl"):asset.endsWith(".ts")?MediaType.parseMediaType("video/mp2t"):MediaType.IMAGE_JPEG;return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").contentType(type).body(new FileSystemResource(file));}
 private String sha256(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
