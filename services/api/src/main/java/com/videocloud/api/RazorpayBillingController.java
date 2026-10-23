package com.videocloud.api;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
@RestController
public class RazorpayBillingController {
 public record CheckoutRequest(String planCode){}
 public record CheckoutResponse(String subscriptionId,String shortUrl,String status,String mode){}
 private final JdbcTemplate jdbc;private final CurrentWorkspace workspaces;private final ObjectMapper mapper;
 private final String mode,keyId,keySecret,webhookSecret,starterPlan,proPlan;
 private final RestClient client=RestClient.create();
 public RazorpayBillingController(JdbcTemplate jdbc,CurrentWorkspace workspaces,ObjectMapper mapper,
  @Value("${billing.razorpay.mode:test}")String mode,@Value("${billing.razorpay.key-id:}")String keyId,
  @Value("${billing.razorpay.key-secret:}")String keySecret,@Value("${billing.razorpay.webhook-secret:}")String webhookSecret,
  @Value("${billing.razorpay.plan-starter:}")String starterPlan,@Value("${billing.razorpay.plan-pro:}")String proPlan){
  this.jdbc=jdbc;this.workspaces=workspaces;this.mapper=mapper;this.mode=mode;this.keyId=keyId;this.keySecret=keySecret;this.webhookSecret=webhookSecret;this.starterPlan=starterPlan;this.proPlan=proPlan;
 }
 @PostMapping("/api/v1/billing/checkout")
 public CheckoutResponse checkout(@RequestBody CheckoutRequest request,Authentication auth){
  if(!"test".equalsIgnoreCase(mode))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Only Razorpay test mode is permitted by this local build");
  if(keyId.isBlank()||keySecret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure Razorpay test keys in local environment");
  String code=request.planCode()==null?"":request.planCode().trim().toUpperCase(Locale.ROOT);
  String providerPlan=switch(code){case "STARTER"->starterPlan;case "PRO"->proPlan;default->"";};
  if(providerPlan.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose STARTER or PRO and configure its Razorpay test plan ID");
  UUID ws=workspaces.id(auth),planId;
  try{planId=jdbc.queryForObject("SELECT id FROM plans WHERE code=? AND active=true",UUID.class,code);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown plan");}
  Map<String,Object> body=Map.of("plan_id",providerPlan,"total_count",12,"customer_notify",1,"notes",Map.of("workspace_id",ws.toString(),"plan_code",code));
  JsonNode response;
  try{response=client.post().uri("https://api.razorpay.com/v1/subscriptions").headers(h->h.setBasicAuth(keyId,keySecret)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);}
  catch(RestClientException e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Razorpay test API request failed");}
  if(response==null||response.path("id").asText().isBlank())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Razorpay returned an invalid subscription response");
  String id=response.path("id").asText(),url=response.path("short_url").asText("");
  jdbc.update("INSERT INTO subscriptions(workspace_id,plan_id,provider,provider_subscription_id,status,currency) VALUES (?,?,'RAZORPAY',?,'INCOMPLETE','INR') ON CONFLICT(provider_subscription_id) DO NOTHING",ws,planId,id);
  return new CheckoutResponse(id,url,"INCOMPLETE","test");
 }
 @PostMapping(value="/api/v1/webhooks/razorpay",consumes=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<Map<String,String>> webhook(@RequestBody byte[] raw,@RequestHeader(value="X-Razorpay-Signature",required=false)String signature,@RequestHeader(value="X-Razorpay-Event-Id",required=false)String eventId){
  if(webhookSecret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Webhook secret is not configured");
  if(signature==null||!constantTimeEquals(hmac(raw,webhookSecret),signature))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
  JsonNode event;try{event=mapper.readTree(raw);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid webhook JSON");}
  String type=event.path("event").asText("unknown");if(eventId==null||eventId.isBlank())eventId=sha256(raw);
  int inserted=jdbc.update("INSERT INTO billing_provider_events(provider,provider_event_id,event_type,payload_sha256) VALUES ('RAZORPAY',?,?,?) ON CONFLICT(provider,provider_event_id) DO NOTHING",eventId,type,sha256(raw));
  if(inserted==0)return ResponseEntity.ok(Map.of("status","duplicate_ignored"));
  String subId=event.path("payload").path("subscription").path("entity").path("id").asText("");
  if(subId.isBlank())subId=event.path("payload").path("payment").path("entity").path("subscription_id").asText("");
  String state=switch(type){case "subscription.activated","subscription.charged"->"ACTIVE";case "subscription.pending","subscription.halted"->"PAST_DUE";case "subscription.cancelled"->"CANCELLED";case "subscription.completed"->"EXPIRED";default->"";};
  if(!subId.isBlank()&&!state.isBlank())jdbc.update("UPDATE subscriptions SET status=?,updated_at=now() WHERE provider='RAZORPAY' AND provider_subscription_id=?",state,subId);
  jdbc.update("UPDATE billing_provider_events SET processing_status='PROCESSED',processed_at=now() WHERE provider='RAZORPAY' AND provider_event_id=?",eventId);
  return ResponseEntity.ok(Map.of("status","processed"));
 }
 private String hmac(byte[] data,String secret){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(data));}catch(Exception e){throw new IllegalStateException(e);}}
 private boolean constantTimeEquals(String a,String b){try{return MessageDigest.isEqual(HexFormat.of().parseHex(a),HexFormat.of().parseHex(b));}catch(IllegalArgumentException e){return false;}}
 private String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
}
