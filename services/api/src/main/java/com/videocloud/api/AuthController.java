package com.videocloud.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
 public record RegisterRequest(@Email @NotBlank @Size(max=320) String email,@NotBlank @Size(min=12,max=72) String password,@NotBlank @Size(min=2,max=160) String displayName,@NotBlank @Size(min=2,max=160) String workspaceName){}
 public record RegisterResponse(String userId,String email,String workspaceId,String workspaceName,String authentication,String note){}
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;
 public AuthController(JdbcTemplate jdbc,PasswordEncoder encoder){this.jdbc=jdbc;this.encoder=encoder;}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public RegisterResponse register(@Valid @RequestBody RegisterRequest r){String email=r.email().trim().toLowerCase(Locale.ROOT);try{UUID user=jdbc.queryForObject("INSERT INTO users(email,password_hash,display_name,identity_provider) VALUES (?,?,?,'LOCAL') RETURNING id",UUID.class,email,encoder.encode(r.password()),r.displayName().trim());String slug=r.workspaceName().trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");if(slug.isBlank())slug="workspace";slug=slug.substring(0,Math.min(slug.length(),75)+"".length())+"-"+user.toString().substring(0,8);UUID ws=jdbc.queryForObject("INSERT INTO workspaces(name,slug,owner_user_id) VALUES (?,?,?) RETURNING id",UUID.class,r.workspaceName().trim(),slug,user);jdbc.update("INSERT INTO workspace_members(workspace_id,user_id,role) VALUES (?,?,'OWNER')",ws,user);return new RegisterResponse(user.toString(),email,ws.toString(),r.workspaceName().trim(),"HTTP Basic","Local MVP only. Use this email and chosen password for API requests; replace with HTTPS/OIDC before production.");}catch(DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists");}}
}