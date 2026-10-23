package com.videocloud.video;
import com.videocloud.api.CurrentWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/v1/videos/{videoId}/comments")
public class CommentController {
 public record CreateComment(@NotBlank @Size(max=4000) String body,@Min(0) Integer timestampSeconds){}
 public record CommentDto(UUID id,String author,String body,Integer timestampSeconds,String createdAt){}
 private final JdbcTemplate jdbc;private final CurrentWorkspace workspaces;
 public CommentController(JdbcTemplate jdbc,CurrentWorkspace workspaces){this.jdbc=jdbc;this.workspaces=workspaces;}
 private void assertOwned(UUID video,Authentication auth){var rows=jdbc.query("SELECT id FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",(rs,n)->rs.getObject(1,UUID.class),video,workspaces.id(auth));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");}
 @GetMapping public List<CommentDto> list(@PathVariable UUID videoId,Authentication auth){assertOwned(videoId,auth);return jdbc.query("SELECT c.id,u.display_name,c.body,c.timestamp_seconds,c.created_at FROM video_comments c JOIN users u ON u.id=c.author_user_id WHERE c.video_id=? AND c.deleted_at IS NULL ORDER BY c.created_at",(rs,n)->new CommentDto(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("body"),(Integer)rs.getObject("timestamp_seconds"),rs.getTimestamp("created_at").toInstant().toString()),videoId);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public CommentDto create(@PathVariable UUID videoId,@Valid @RequestBody CreateComment request,Authentication auth){assertOwned(videoId,auth);UUID id=jdbc.queryForObject("INSERT INTO video_comments(video_id,author_user_id,body,timestamp_seconds) VALUES (?,?,?,?) RETURNING id",UUID.class,videoId,workspaces.userId(auth),request.body().trim(),request.timestampSeconds());return jdbc.queryForObject("SELECT c.id,u.display_name,c.body,c.timestamp_seconds,c.created_at FROM video_comments c JOIN users u ON u.id=c.author_user_id WHERE c.id=?",(rs,n)->new CommentDto(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("body"),(Integer)rs.getObject("timestamp_seconds"),rs.getTimestamp("created_at").toInstant().toString()),id);}
 @DeleteMapping("/{commentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID videoId,@PathVariable UUID commentId,Authentication auth){assertOwned(videoId,auth);int n=jdbc.update("UPDATE video_comments SET deleted_at=now() WHERE id=? AND video_id=? AND author_user_id=? AND deleted_at IS NULL",commentId,videoId,workspaces.userId(auth));if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Comment not found or not owned by you");}
}
