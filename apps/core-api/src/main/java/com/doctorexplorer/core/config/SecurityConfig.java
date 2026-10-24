package com.doctorexplorer.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Camada de QoS: regras de rota centralizadas aqui; checagens de dono/papel/selo ficam no domínio (RF12).
 * CSRF no padrão SPA (cookie XSRF-TOKEN + cabeçalho X-XSRF-TOKEN em toda escrita), CSP fechada,
 * Referrer-Policy no-referrer, troca do id de sessão no login. Nunca JWT no cliente (ADR-008).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.spa())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicy.NO_REFERRER))
                .frameOptions(fo -> fo.deny())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(63072000)))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fix -> fix.changeSessionId()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/login/2fa").permitAll()
                .requestMatchers("/api/v1/ping", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/institutions", "/api/v1/institutions/*").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("MODERATOR", "ADMIN")
                .requestMatchers("/api/v1/privacy/dpo/**").hasRole("DPO")
                .anyRequest().authenticated())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            // Sem sessão: 401 (e não o 403 padrão de uma cadeia sem formulário nem Basic). Sem papel: 403.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
