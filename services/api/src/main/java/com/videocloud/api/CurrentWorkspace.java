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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
/** Resolves a workspace only from authenticated membership, never from an untrusted ID alone. */
@Component
public class CurrentWorkspace {
    private static final List<String> ROLE_ORDER = List.of("OWNER", "ADMIN", "EDITOR", "VIEWER");

    private final JdbcTemplate jdbc;

    public CurrentWorkspace(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID id(Authentication authentication) {
        return resolve(authentication).workspaceId();
    }

    /** Returns the caller's role in the resolved workspace. */
    public String role(Authentication authentication) {
        return resolve(authentication).role();
    }

    /**
     * Asserts the caller's role is one of the allowed roles; throws 403 otherwise.
     * Roles: OWNER > ADMIN > EDITOR > VIEWER.
     */
    public UUID assertAtLeast(Authentication authentication, String... allowedRoles) {
        Membership m = resolve(authentication);
        Set<String> allowed = Set.of(allowedRoles);
        if (!allowed.contains(m.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Your role (" + m.role() + ") is not permitted to perform this action.");
        }
        return m.workspaceId();
    }

    public UUID userId(Authentication authentication) {
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email)=lower(?)",
            UUID.class, authentication.getName());
    }

    private record Membership(UUID workspaceId, String role) {}

    private Membership resolve(Authentication authentication) {
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
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT w.id, m.role FROM workspaces w JOIN workspace_members m ON m.workspace_id=w.id " +
                "JOIN users u ON u.id=m.user_id WHERE w.id=? AND lower(u.email)=lower(?)",
                workspaceId, authentication.getName());
            if (rows.isEmpty()) {
                // Avoid revealing whether a foreign workspace exists.
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
            }
            return new Membership((UUID) rows.get(0).get("id"), (String) rows.get(0).get("role"));
        }

        List<Map<String, Object>> memberships = jdbc.queryForList(
            "SELECT w.id, m.role FROM workspaces w JOIN workspace_members m ON m.workspace_id=w.id " +
            "JOIN users u ON u.id=m.user_id WHERE lower(u.email)=lower(?) " +
            "ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 WHEN 'EDITOR' THEN 2 ELSE 3 END,w.created_at,w.id",
            authentication.getName());
        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No workspace membership");
        }
        if (memberships.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Multiple workspaces are available. Send X-Workspace-Id to select the workspace.");
        }
        return new Membership((UUID) memberships.get(0).get("id"), (String) memberships.get(0).get("role"));
    }

    /** Only workspace owners, admins, and editors may mutate workspace media. */
    public void requireEditor(Authentication authentication, UUID workspaceId) {
        requireRole(authentication, workspaceId, List.of("OWNER", "ADMIN", "EDITOR"), "Workspace editor permission required");
    }

    /** Only workspace owners and admins may delete media. */
    public void requireOwnerOrAdmin(Authentication authentication, UUID workspaceId) {
        requireRole(authentication, workspaceId, List.of("OWNER", "ADMIN"), "Only workspace owners and admins may delete media");
    }

    private void requireRole(Authentication authentication, UUID workspaceId, List<String> allowed, String message) {
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
        if (!allowed.contains(roles.get(0))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private String requestedWorkspace() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader("X-Workspace-Id");
    }
}
