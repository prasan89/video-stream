package com.videocloud.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lists only workspaces the authenticated account is a member of. */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final JdbcTemplate jdbc;

    public WorkspaceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String,Object>> list(Authentication authentication) {
        return jdbc.query(
            "SELECT w.id,w.name,w.slug,m.role FROM workspaces w " +
            "JOIN workspace_members m ON m.workspace_id=w.id JOIN users u ON u.id=m.user_id " +
            "WHERE lower(u.email)=lower(?) ORDER BY w.name,w.id",
            (rs, row) -> Map.of(
                "id", rs.getObject("id", UUID.class).toString(),
                "name", rs.getString("name"),
                "slug", rs.getString("slug"),
                "role", rs.getString("role")),
            authentication.getName());
    }
}
