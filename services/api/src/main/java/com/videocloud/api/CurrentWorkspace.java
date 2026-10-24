package com.videocloud.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Resolves a workspace only from authenticated membership, never from an untrusted ID alone. */
@Component
public class CurrentWorkspace {
    private final JdbcTemplate jdbc;

    public CurrentWorkspace(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID id(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String requested = requestedWorkspace();
        if (requested != null && !requested.isBlank()) {
            UUID workspaceId;
            try { workspaceId = UUID.fromString(requested.trim()); }
            catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Workspace-Id must be a UUID");
            }
            List<UUID> membership = jdbc.query(
                "SELECT w.id FROM workspaces w JOIN workspace_members m ON m.workspace_id=w.id " +
                "JOIN users u ON u.id=m.user_id WHERE w.id=? AND lower(u.email)=lower(?)",
                (rs, row) -> rs.getObject(1, UUID.class), workspaceId, authentication.getName());
            if (membership.isEmpty()) {
                // Avoid revealing whether a foreign workspace exists.
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
            }
            return membership.get(0);
        }

        List<UUID> memberships = jdbc.query(
            "SELECT w.id FROM workspaces w JOIN workspace_members m ON m.workspace_id=w.id " +
            "JOIN users u ON u.id=m.user_id WHERE lower(u.email)=lower(?) " +
            "ORDER BY CASE WHEN m.role='OWNER' THEN 0 ELSE 1 END,w.created_at,w.id",
            (rs, row) -> rs.getObject(1, UUID.class), authentication.getName());
        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No workspace membership");
        }
        if (memberships.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Multiple workspaces are available. Send X-Workspace-Id to select the workspace.");
        }
        return memberships.get(0);
    }

    /** Only workspace owners, admins, and editors may mutate workspace media. */
    public void requireEditor(Authentication authentication, UUID workspaceId) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        List<String> roles = jdbc.query(
            "SELECT m.role FROM workspace_members m JOIN users u ON u.id=m.user_id " +
            "WHERE m.workspace_id=? AND lower(u.email)=lower(?)",
            (rs, row) -> rs.getString(1), workspaceId, authentication.getName());
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        if (!List.of("OWNER", "ADMIN", "EDITOR").contains(roles.get(0))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace editor permission required");
        }
    }

    public UUID userId(Authentication authentication) {
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email)=lower(?)",
            UUID.class, authentication.getName());
    }

    private String requestedWorkspace() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader("X-Workspace-Id");
    }
}
