package com.videocloud.api;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/usage") public class UsageController {
 private final JdbcTemplate jdbc;private final CurrentWorkspace workspace;public UsageController(JdbcTemplate jdbc,CurrentWorkspace workspace){this.jdbc=jdbc;this.workspace=workspace;}
 @GetMapping public Map<String,Object> usage(Authentication auth){var ws=workspace.id(auth);Integer count=jdbc.queryForObject("SELECT count(*) FROM videos WHERE workspace_id=? AND deleted_at IS NULL",Integer.class,ws);Long bytes=jdbc.queryForObject("SELECT COALESCE(sum(a.size_bytes),0) FROM video_assets a JOIN videos v ON v.id=a.video_id WHERE v.workspace_id=? AND v.deleted_at IS NULL AND a.asset_type='ORIGINAL'",Long.class,ws);return Map.of("workspaceId",ws.toString(),"videoCount",count==null?0:count,"storedBytes",bytes==null?0L:bytes,"billingMode","NOT_CONFIGURED","note","Local development only; no payment is taken.");}
}