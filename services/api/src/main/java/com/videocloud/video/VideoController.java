package com.videocloud.video;
import com.videocloud.api.CurrentWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
@RestController @RequestMapping("/api/v1/videos")
public class VideoController {
 public record VideoDto(UUID id,String title,String description,String visibility,String status,Long durationSeconds,String createdAt,String folderName){}
 public record UpdateVideoRequest(@NotBlank @Size(max=250) String title,@Size(max=10000) String description,String visibility,@Size(max=80) String folderName){}
 private static final Set<String>TYPES=Set.of("video/mp4","video/quicktime","video/webm","video/x-matroska");
 private static final Pattern ASSET=Pattern.compile("(index\\.m3u8|thumbnail\\.jpg|segment-[0-9]{5}\\.ts)");
 private final JdbcTemplate jdbc;private final CurrentWorkspace workspaces;private final VideoProcessingService processor;private final WorkspaceQuotaService quotas;private final Path root;private final long maxBytes;
 public VideoController(JdbcTemplate jdbc,CurrentWorkspace workspaces,VideoProcessingService processor,WorkspaceQuotaService quotas,@Value("${videocloud.media-root:./var/media}")String mediaRoot,@Value("${videocloud.max-upload-bytes:2147483648}")long maxBytes){this.jdbc=jdbc;this.workspaces=workspaces;this.processor=processor;this.quotas=quotas;this.root=Path.of(mediaRoot).toAbsolutePath().normalize();this.maxBytes=maxBytes;}
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.ACCEPTED) @Transactional
 public VideoDto upload(@RequestPart("file")MultipartFile file,@RequestParam("title")@NotBlank@Size(max=250)String title,Authentication auth)throws IOException{
  if(file.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Empty upload");if(file.getSize()>maxBytes)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Upload limit exceeded");
  if(file.getContentType()==null||!TYPES.contains(file.getContentType()))throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Allowed formats: MP4, MOV, WebM, MKV");
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("video.mp4");String ext=name.lastIndexOf('.')>=0?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"mp4";if(!Set.of("mp4","mov","webm","mkv").contains(ext))throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Unsupported extension");
  UUID id=UUID.randomUUID(),ws=workspaces.id(auth),user=workspaces.userId(auth);workspaces.requireEditor(auth,ws);quotas.assertCanStartUpload(ws,file.getSize());Path dir=root.resolve("workspaces").resolve(ws.toString()).resolve("videos").resolve(id.toString()).normalize();if(!dir.startsWith(root.resolve("workspaces").resolve(ws.toString()).normalize()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid tenant storage path");Files.createDirectories(dir);Path source=dir.resolve("source."+ext);
  try{file.transferTo(source);VideoFileValidator.validate(source,ext,file.getContentType());jdbc.update("INSERT INTO videos(id,workspace_id,title,visibility,status,source_object_key,created_by) VALUES (?,?,?,'PRIVATE','PROCESSING',?,?)",id,ws,title.trim(),root.relativize(source).toString(),user);jdbc.update("INSERT INTO video_assets(video_id,asset_type,object_key,mime_type,size_bytes) VALUES (?,'ORIGINAL',?,?,?)",id,root.relativize(source).toString(),file.getContentType(),file.getSize());}catch(IOException | RuntimeException e){deleteTree(dir);throw e;}
  processor.process(id,source,dir);return find(id,ws);
 }
 @GetMapping public List<VideoDto> list(Authentication auth){return jdbc.query("SELECT id,title,description,visibility,status,duration_seconds,created_at,folder_name FROM videos WHERE workspace_id=? AND deleted_at IS NULL ORDER BY created_at DESC",(rs,n)->dto(rs),workspaces.id(auth));}
 @GetMapping("/{id}")public VideoDto get(@PathVariable UUID id,Authentication auth){return find(id,workspaces.id(auth));}
 @PatchMapping("/{id}")public VideoDto update(@PathVariable UUID id,@Valid @RequestBody UpdateVideoRequest r,Authentication auth){if(r.visibility()!=null&&!Set.of("PRIVATE","UNLISTED","PUBLIC").contains(r.visibility()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid visibility");UUID ws=workspaces.id(auth);workspaces.requireEditor(auth,ws);int n=jdbc.update("UPDATE videos SET title=?,description=?,visibility=COALESCE(?,visibility),folder_name=?,updated_at=now() WHERE id=? AND workspace_id=? AND deleted_at IS NULL",r.title().trim(),r.description(),r.visibility(),normalizeFolder(r.folderName()),id,ws);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");return find(id,ws);}
 @DeleteMapping("/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)public void delete(@PathVariable UUID id,Authentication auth){UUID ws=workspaces.id(auth);workspaces.requireEditor(auth,ws);List<String> keys=jdbc.query("SELECT source_object_key FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",(rs,n)->rs.getString(1),id,ws);if(keys.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");int n=jdbc.update("UPDATE videos SET status='DELETED',deleted_at=now(),updated_at=now() WHERE id=? AND workspace_id=? AND deleted_at IS NULL",id,ws);if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");Path source=root.resolve(keys.get(0)).normalize();if(source.startsWith(root))deleteTree(source.getParent());}
 @PostMapping("/{id}/retry-processing") @ResponseStatus(HttpStatus.ACCEPTED)
 public Map<String,Object> retryProcessing(@PathVariable UUID id,Authentication auth){
  UUID ws=workspaces.id(auth);workspaces.requireEditor(auth,ws);var rows=jdbc.query("SELECT source_object_key,status FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",(rs,n)->Map.of("source",rs.getString("source_object_key"),"status",rs.getString("status")),id,ws);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");
  if(!"FAILED".equals(rows.get(0).get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only failed videos can be retried");
  Path source=root.resolve((String)rows.get(0).get("source")).normalize();if(!source.startsWith(root)||!Files.isRegularFile(source))throw new ResponseStatusException(HttpStatus.CONFLICT,"Original file is missing; upload again");
  jdbc.update("UPDATE videos SET status='PROCESSING',updated_at=now() WHERE id=? AND workspace_id=?",id,ws);processor.process(id,source,source.getParent());
  return Map.of("videoId",id.toString(),"status","PROCESSING");
 }
 @GetMapping("/{id}/assets/{asset}")public ResponseEntity<Resource> asset(@PathVariable UUID id,@PathVariable String asset,Authentication auth){UUID ws=workspaces.id(auth);var rows=jdbc.query("SELECT status FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",(rs,n)->rs.getString(1),id,ws);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");if(!ASSET.matcher(asset).matches())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found");String sourceKey=jdbc.queryForObject("SELECT source_object_key FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",String.class,id,ws);Path source=root.resolve(sourceKey).normalize();if(!source.startsWith(root))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found");Path dir=source.getParent();Path file=dir.resolve(asset).normalize();if(!file.startsWith(dir)||!Files.isRegularFile(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found");MediaType type=asset.endsWith(".m3u8")?MediaType.parseMediaType("application/vnd.apple.mpegurl"):asset.endsWith(".ts")?MediaType.parseMediaType("video/mp2t"):MediaType.IMAGE_JPEG;return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").contentType(type).body(new FileSystemResource(file));}
 private VideoDto find(UUID id,UUID ws){var rows=jdbc.query("SELECT id,title,description,visibility,status,duration_seconds,created_at,folder_name FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",(rs,n)->dto(rs),id,ws);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");return rows.get(0);}
 private VideoDto dto(java.sql.ResultSet r)throws java.sql.SQLException{Object d=r.getObject("duration_seconds");return new VideoDto(r.getObject("id",UUID.class),r.getString("title"),r.getString("description"),r.getString("visibility"),r.getString("status"),d==null?null:((Number)d).longValue(),r.getTimestamp("created_at").toInstant().toString(),r.getString("folder_name"));}
 private String normalizeFolder(String folder){if(folder==null||folder.isBlank())return null;String normalized=folder.trim().replaceAll("\\s+"," ");if(normalized.length()>80)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Folder name must be 80 characters or fewer");if(normalized.contains("/")||normalized.contains("\\"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Folder name cannot contain path separators");return normalized;}
 private void deleteTree(Path dir){if(dir.normalize().equals(root)||!dir.normalize().startsWith(root)||!Files.exists(dir))return;try(var paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
}