package com.doctorexplorer.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sessão ociosa expira em 60 min (deslizante, Spring Session); esta é a vida máxima **absoluta** de 24 h
 * contada do login, qualquer que seja a atividade. Não é bean: entra só na cadeia do Spring Security
 * (SecurityConfig), para o contêiner de servlet não registrá-lo uma segunda vez.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    public static final String STARTED_AT = "de.session.startedAt";

    private final AppSecretProperties props;

    public AbsoluteSessionTimeoutFilter(AppSecretProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(STARTED_AT) instanceof Long started
                && Instant.ofEpochMilli(started).plus(props.session().absoluteTimeout()).isBefore(Instant.now())) {
            session.invalidate();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
