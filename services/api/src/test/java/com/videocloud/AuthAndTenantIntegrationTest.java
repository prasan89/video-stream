package com.videocloud;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class AuthAndTenantIntegrationTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;
 @Test void registrationCreatesOwnerAndVideoListIsTenantScoped()throws Exception{
  String suffix=UUID.randomUUID().toString().substring(0,8),password="Local-only-pass-123",emailA="owner-"+suffix+"@example.test",emailB="other-"+suffix+"@example.test";
  register(emailA,password,"Owner Studio");register(emailB,password,"Other Studio");
  UUID userA=jdbc.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,emailA);
  UUID workspaceA=jdbc.queryForObject("SELECT id FROM workspaces WHERE owner_user_id=?",UUID.class,userA);
  jdbc.update("INSERT INTO videos(workspace_id,title,visibility,status,created_by) VALUES (?,?,'PRIVATE','CREATED',?)",workspaceA,"Secret video",userA);
  mvc.perform(get("/api/v1/videos").with(httpBasic(emailB,password))).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(get("/api/v1/videos").with(httpBasic(emailA,password))).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Secret video"));
  mvc.perform(get("/api/v1/usage")).andExpect(status().isUnauthorized());
 }
 private void register(String email,String password,String workspace)throws Exception{
  var body=Map.of("email",email,"password",password,"displayName","Local Test","workspaceName",workspace);
  mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isCreated());
 }
}
