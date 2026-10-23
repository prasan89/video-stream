package com.videocloud;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
class LocalSchemaIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Test void flywayCreatesExpectedLocalMvpTables(){
  Integer users=jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='users'",Integer.class);
  Integer videos=jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='videos'",Integer.class);
  Integer plans=jdbc.queryForObject("SELECT count(*) FROM plans WHERE code IN ('FREE','STARTER','PRO')",Integer.class);
  assertThat(users).isEqualTo(1);assertThat(videos).isEqualTo(1);assertThat(plans).isEqualTo(3);
 }
}
