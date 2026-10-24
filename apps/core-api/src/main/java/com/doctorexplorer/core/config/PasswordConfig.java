package com.doctorexplorer.core.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Argon2id como padrão via DelegatingPasswordEncoder; bcrypt só para ler hashes antigos, se um dia houver. */
@Configuration
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2, "bcrypt", new BCryptPasswordEncoder()));
    }
}
