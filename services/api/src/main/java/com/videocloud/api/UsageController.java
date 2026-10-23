package com.videocloud.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {
 private final JdbcTemplate jdbc;
 private final CurrentWorkspace workspace;
 public UsageController(JdbcTemplate jdbc,CurrentWorkspace workspace){this.jdbc=jdbc;this.workspace=workspace;}

 @GetMapping
 public Map<String,Object> usage(Authentication auth){
  var ws=workspace.id(auth);
  Integer count=jdbc.queryForObject("SELECT count(*) FROM videos WHERE workspace_id=? AND deleted_at IS NULL AND status<>'DELETED'",Integer.class,ws);
  Long bytes=jdbc.queryForObject("SELECT COALESCE(sum(a.size_bytes),0) FROM video_assets a JOIN videos v ON v.id=a.video_id WHERE v.workspace_id=? AND v.deleted_at IS NULL AND a.asset_type='ORIGINAL'",Long.class,ws);
  Long reserved=jdbc.queryForObject("SELECT COALESCE(sum(us.expected_size_bytes),0) FROM upload_sessions us JOIN videos v ON v.id=us.video_id WHERE v.workspace_id=? AND v.deleted_at IS NULL AND us.status IN ('CREATED','UPLOADING') AND us.expires_at>now()",Long.class,ws);
  Map<String,Object> plan=jdbc.queryForMap("SELECT p.code,p.storage_limit_bytes,p.video_limit FROM plans p WHERE p.active=true AND p.id=COALESCE((SELECT s.plan_id FROM subscriptions s WHERE s.workspace_id=? AND s.status IN ('ACTIVE','TRIALING') AND (s.current_period_end IS NULL OR s.current_period_end>now()) ORDER BY s.created_at DESC LIMIT 1),(SELECT id FROM plans WHERE code='FREE' AND active=true))",ws);
  long stored=bytes==null?0L:bytes, reservedBytes=reserved==null?0L:reserved, limit=((Number)plan.get("storage_limit_bytes")).longValue();
  return Map.of("workspaceId",ws.toString(),"videoCount",count==null?0:count,"storedBytes",stored,
   "reservedUploadBytes",reservedBytes,"storageLimitBytes",limit,"availableBytes",Math.max(0L,limit-stored-reservedBytes),
   "videoLimit",((Number)plan.get("video_limit")).intValue(),"planCode",plan.get("code"),"billingMode","NOT_CONFIGURED",
   "note","Storage is original-file bytes plus reserved in-progress uploads; derived HLS/thumbnail bytes are not included. Local development only.");
 }
}
