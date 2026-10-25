package com.doctorexplorer.core.internal;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.identity.web.SessionPrincipal;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.web.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF08 · porta do prontuário. O navegador só fala com a core-api (sessão opaca + CSRF); aqui a sessão vira um token
 * interno de 60 s e a chamada segue para a clinical-api, que tem banco e chaves próprios. Nada do corpo é lido ou
 * registrado aqui: o conteúdo clínico só existe na clinical-api.
 */
@RestController
public class ClinicalGatewayController {

    static final String PREFIX = "/api/v1/clinical";
    private static final Set<String> SAFE = Set.of("GET", "HEAD");
    private static final Set<String> FORWARDED_RESPONSE = Set.of("content-type", "etag", "last-modified", "location");
    /** Só os recursos da clinical-api; segmentos simples (sem "..", "//", "%" nem "@"), então o destino é sempre a base. */
    /** Consulta já codificada pelo navegador: só caracteres de URL, sem fragmento nem espaço. */
    static final Pattern SAFE_QUERY = Pattern.compile("^[A-Za-z0-9._~%&=+:/,-]*$");
    static final Pattern ALLOWED_PATH = Pattern.compile("^/(patients|compositions|terminology|fhir/r4)(/[A-Za-z0-9._-]+)*$");

    private final AccessService access;
    private final SessionSupport sessions;
    private final CredentialingService credentialing;
    private final InternalTokens tokens;
    private final ClinicalProperties props;
    private final HttpClient http;

    public ClinicalGatewayController(AccessService access, SessionSupport sessions, CredentialingService credentialing, InternalTokens tokens,
            ClinicalProperties props) {
        this.access = access;
        this.sessions = sessions;
        this.credentialing = credentialing;
        this.tokens = tokens;
        this.props = props;
        this.http = HttpClient.newBuilder().connectTimeout(props.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @RequestMapping(PREFIX + "/**")
    ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
        SessionPrincipal p = access.requireActive();
        String path = request.getRequestURI().substring(request.getContextPath().length() + PREFIX.length());
        if (!ALLOWED_PATH.matcher(path).matches() || path.contains("..")) {
            throw new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "Esse recurso do prontuário não existe.");
        }
        String query = request.getQueryString();
        if (query != null && !SAFE_QUERY.matcher(query).matches()) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "consulta-invalida", "Consulta inválida", "Parâmetros da consulta inválidos.");
        }
        var status = credentialing.status(p.userId());
        HttpSession session = request.getSession(false);
        Object sudo = session == null ? null : session.getAttribute(SessionSupport.SUDO_UNTIL);
        Object mfa = session == null ? null : session.getAttribute(SessionSupport.MFA_ENABLED);
        String method = request.getMethod();
        String token = tokens.issue(p.userId(), p.displayName(), status.crmNumber(), status.crmUf(), String.valueOf(status.seal()),
                sudo instanceof Long l ? l : 0L, Boolean.TRUE.equals(mfa), !SAFE.contains(method), InternalTokens.CLINICAL);

        // esquema, host e porta vêm só da configuração; o caminho já passou pela lista de recursos
        URI base = URI.create(props.baseUrl());
        URI target = URI.create(base.getScheme() + "://" + base.getRawAuthority() + path + (query == null ? "" : "?" + query));
        byte[] body = request.getInputStream().readAllBytes();
        HttpRequest.Builder b = HttpRequest.newBuilder(target).timeout(props.timeout())
                .header("Authorization", "DE-Internal " + token)
                .header("Accept", request.getHeader("Accept") == null ? "application/json" : request.getHeader("Accept"))
                .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (request.getContentType() != null && body.length > 0) b.header("Content-Type", request.getContentType());
        HttpResponse<byte[]> res;
        try {
            res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
        ResponseEntity.BodyBuilder out = ResponseEntity.status(res.statusCode());
        res.headers().map().forEach((k, v) -> {
            if (FORWARDED_RESPONSE.contains(k.toLowerCase())) v.forEach(value -> out.header(k, value));
        });
        out.header("Cache-Control", "no-store");
        return out.body(res.body());
    }

    private static ApiProblemException unavailable() {
        return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "prontuario-indisponivel", "Prontuário indisponível",
                "O serviço do prontuário não respondeu. Nada foi gravado; tente de novo em instantes.");
    }
}
