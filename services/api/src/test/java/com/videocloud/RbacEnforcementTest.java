package com.videocloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifies that workspace role restrictions are enforced on mutating endpoints. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class RbacEnforcementTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private static final String PASSWORD = "Local-only-pass-999";

    @Test
    void viewerCannotDeleteVideo() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-rbac-" + suffix + "@example.test";
        String viewerEmail = "viewer-rbac-" + suffix + "@example.test";

        register(ownerEmail, PASSWORD, "Owner WS " + suffix);
        register(viewerEmail, PASSWORD, "Viewer WS " + suffix);

        UUID ownerId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, ownerEmail);
        UUID viewerId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, viewerEmail);
        UUID wsId = jdbc.queryForObject("SELECT id FROM workspaces WHERE owner_user_id=?", UUID.class, ownerId);

        // Add the viewer as a VIEWER member of the owner's workspace
        jdbc.update("INSERT INTO workspace_members(workspace_id, user_id, role) VALUES (?,?,'VIEWER')", wsId, viewerId);

        // Owner inserts a video directly
        UUID videoId = UUID.randomUUID();
        jdbc.update("INSERT INTO videos(id,workspace_id,title,visibility,status,created_by) VALUES (?,?,?,'PRIVATE','READY',?)",
            videoId, wsId, "Owner video", ownerId);

        // VIEWER trying to delete the video must receive 403
        mvc.perform(delete("/api/v1/videos/" + videoId)
                .with(httpBasic(viewerEmail, PASSWORD))
                .header("X-Workspace-Id", wsId.toString()))
            .andExpect(status().isForbidden());

        // VIEWER trying to update the video must receive 403
        mvc.perform(patch("/api/v1/videos/" + videoId)
                .with(httpBasic(viewerEmail, PASSWORD))
                .header("X-Workspace-Id", wsId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("title", "Hacked", "description", "", "visibility", "PUBLIC"))))
            .andExpect(status().isForbidden());

        // OWNER can still see the video
        mvc.perform(get("/api/v1/videos/" + videoId)
                .with(httpBasic(ownerEmail, PASSWORD))
                .header("X-Workspace-Id", wsId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Owner video"));
    }

    @Test
    void editorCanUploadButNotDelete() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-ed-" + suffix + "@example.test";
        String editorEmail = "editor-ed-" + suffix + "@example.test";

        register(ownerEmail, PASSWORD, "Ed Owner WS " + suffix);
        register(editorEmail, PASSWORD, "Ed WS " + suffix);

        UUID ownerId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, ownerEmail);
        UUID editorId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, editorEmail);
        UUID wsId = jdbc.queryForObject("SELECT id FROM workspaces WHERE owner_user_id=?", UUID.class, ownerId);

        jdbc.update("INSERT INTO workspace_members(workspace_id, user_id, role) VALUES (?,?,'EDITOR')", wsId, editorId);

        UUID videoId = UUID.randomUUID();
        jdbc.update("INSERT INTO videos(id,workspace_id,title,visibility,status,created_by) VALUES (?,?,?,'PRIVATE','READY',?)",
            videoId, wsId, "Editor test video", ownerId);

        // EDITOR can update title/description/visibility
        mvc.perform(patch("/api/v1/videos/" + videoId)
                .with(httpBasic(editorEmail, PASSWORD))
                .header("X-Workspace-Id", wsId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("title", "Edited title", "description", "desc", "visibility", "UNLISTED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Edited title"));

        // EDITOR cannot delete
        mvc.perform(delete("/api/v1/videos/" + videoId)
                .with(httpBasic(editorEmail, PASSWORD))
                .header("X-Workspace-Id", wsId.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteVideo() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner-adm-" + suffix + "@example.test";
        String adminEmail = "admin-adm-" + suffix + "@example.test";

        register(ownerEmail, PASSWORD, "Adm Owner WS " + suffix);
        register(adminEmail, PASSWORD, "Adm WS " + suffix);

        UUID ownerId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, ownerEmail);
        UUID adminId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, adminEmail);
        UUID wsId = jdbc.queryForObject("SELECT id FROM workspaces WHERE owner_user_id=?", UUID.class, ownerId);

        jdbc.update("INSERT INTO workspace_members(workspace_id, user_id, role) VALUES (?,?,'ADMIN')", wsId, adminId);

        UUID videoId = UUID.randomUUID();
        jdbc.update("INSERT INTO videos(id,workspace_id,title,visibility,status,created_by) VALUES (?,?,?,'PRIVATE','READY',?)",
            videoId, wsId, "Admin delete test", ownerId);

        mvc.perform(delete("/api/v1/videos/" + videoId)
                .with(httpBasic(adminEmail, PASSWORD))
                .header("X-Workspace-Id", wsId.toString()))
            .andExpect(status().isNoContent());
    }

    private void register(String email, String password, String workspace) throws Exception {
        var body = Map.of("email", email, "password", password, "displayName", "Test User", "workspaceName", workspace);
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(body)))
            .andExpect(status().isCreated());
    }
}
