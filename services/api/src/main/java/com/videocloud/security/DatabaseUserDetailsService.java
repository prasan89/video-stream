package com.videocloud.security;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
@Service public class DatabaseUserDetailsService implements UserDetailsService {
 private final JdbcTemplate jdbc; public DatabaseUserDetailsService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Override public UserDetails loadUserByUsername(String email)throws UsernameNotFoundException{var rows=jdbc.query("SELECT email,password_hash FROM users WHERE lower(email)=lower(?) AND password_hash IS NOT NULL",(rs,n)->User.withUsername(rs.getString("email")).password(rs.getString("password_hash")).roles("USER").build(),email);if(rows.isEmpty())throw new UsernameNotFoundException("Invalid email or password");return rows.get(0);}
}