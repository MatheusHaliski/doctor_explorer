package com.doctorexplorer.clinical.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toda rota (menos a saúde) exige o token interno. Escrita exige token emitido para escrita (a core-api só marca
 * {@code write} depois de validar o CSRF da sessão). Prontuário e FHIR exigem selo VERIFIED; a terminologia, não.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String CALLER = "de.caller";
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    private final InternalTokenVerifier verifier;

    public InternalAuthFilter(InternalTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String h = request.getHeader("Authorization");
        Optional<Caller> caller = h != null && h.startsWith("DE-Internal ") ? verifier.verify(h.substring(12).trim()) : Optional.empty();
        if (caller.isEmpty()) {
            problem(response, 401, "token-interno-invalido", "Chamada não autorizada", "A clinical-api só atende a core-api, com token interno válido.");
            return;
        }
        Caller c = caller.get();
        if (!SAFE.contains(request.getMethod()) && !c.write()) {
            problem(response, 403, "token-somente-leitura", "Token só de leitura", "Esta operação de escrita precisa de um token emitido para escrita.");
            return;
        }
        if (!request.getRequestURI().startsWith("/terminology") && !c.verified()) {
            problem(response, 403, "selo-necessario", "Selo CRM necessário", "O prontuário fica disponível depois que seu CRM for verificado.");
            return;
        }
        request.setAttribute(CALLER, c);
        chain.doFilter(request, response);
    }

    private static void problem(HttpServletResponse response, int status, String type, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        String body = "{\"type\":\"https://doctorexplorer.app/problemas/" + type + "\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
