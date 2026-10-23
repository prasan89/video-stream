package com.videocloud.api;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
@Component public class CurrentWorkspace {
 private final JdbcTemplate jdbc;public CurrentWorkspace(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public UUID id(Authentication a){var rows=jdbc.query("SELECT w.id FROM workspaces w JOIN workspace_members m ON m.workspace_id=w.id JOIN users u ON u.id=m.user_id WHERE lower(u.email)=lower(?) ORDER BY CASE WHEN m.role='OWNER' THEN 0 ELSE 1 END,w.created_at LIMIT 1",(rs,n)->rs.getObject(1,UUID.class),a.getName());if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"No workspace membership");return rows.get(0);}
 public UUID userId(Authentication a){return jdbc.queryForObject("SELECT id FROM users WHERE lower(email)=lower(?)",UUID.class,a.getName());}
}