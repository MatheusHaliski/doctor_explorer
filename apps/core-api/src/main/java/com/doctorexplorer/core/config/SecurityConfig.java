package com.doctorexplorer.core.config;

import com.doctorexplorer.core.access.MfaRequiredAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Camada de QoS: regras de rota centralizadas aqui (RF12, matriz de acesso); checagens de dono/selo ficam
 * no domínio ({@code AccessService}). CSRF no padrão SPA (cookie XSRF-TOKEN + cabeçalho X-XSRF-TOKEN em
 * toda escrita), CSP fechada, Referrer-Policy no-referrer, troca do id de sessão no login. Nunca JWT no
 * cliente (ADR-008).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppSecretProperties props) throws Exception {
        AbsoluteSessionTimeoutFilter absoluteTimeout = new AbsoluteSessionTimeoutFilter(props);
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
            .addFilterBefore(absoluteTimeout, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(auth -> auth
                // visitante
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/login/2fa",
                        "/api/v1/auth/reactivate", "/api/v1/auth/logout").permitAll()
                .requestMatchers("/api/v1/ping", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/institutions", "/api/v1/institutions/*", "/api/v1/profiles/*").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/audit/public-key").permitAll()
                // papéis sensíveis: papel + 2FA ativo. A auditoria também é lida pelo Encarregado (RF13, recorte de privacidade).
                .requestMatchers("/api/v1/admin/audit/**", "/api/v1/admin/audit", "/api/v1/admin/dashboard/**")
                        .access(new MfaRequiredAuthorizationManager("MODERATOR", "ADMIN", "DPO"))
                .requestMatchers("/api/v1/admin/**").access(new MfaRequiredAuthorizationManager("MODERATOR", "ADMIN"))
                .requestMatchers("/api/v1/privacy/dpo/**").access(new MfaRequiredAuthorizationManager("DPO"))
                // o resto exige sessão; selo e estado da conta são checados no domínio
                .anyRequest().authenticated())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            // Sem sessão: 401 (e não o 403 padrão de uma cadeia sem formulário nem Basic). Sem papel: 403.
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
