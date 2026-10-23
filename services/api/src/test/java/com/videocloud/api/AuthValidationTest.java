package com.videocloud.api;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class AuthValidationTest {@Test void passwordHashingUsesSaltedBcrypt(){var encoder=new BCryptPasswordEncoder(12);String hash=encoder.encode("a-long-local-test-password");assertThat(hash).startsWith("$2a$12$");assertThat(encoder.matches("a-long-local-test-password",hash)).isTrue();assertThat(encoder.matches("wrong-password",hash)).isFalse();}}
