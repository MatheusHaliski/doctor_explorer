package com.doctorexplorer.core.internal;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.identity.web.SessionPrincipal;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.web.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
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
    /** Consulta já codificada pelo navegador: só caracteres de URL, sem fragmento nem espaço. */
    static final Pattern SAFE_QUERY = Pattern.compile("^[A-Za-z0-9._~%&=+:/,-]*$");
    /** Só os recursos da clinical-api; segmentos simples (sem "..", "//", "%" nem "@"), então o destino é sempre a base. */
    static final Pattern ALLOWED_PATH = Pattern.compile("^/(patients|compositions|terminology|documents|document-templates|catalog|fhir/r4)(/[A-Za-z0-9._-]+)*$");

    private final AccessService access;
    private final SessionSupport sessions;
    private final CredentialingService credentialing;
    private final InternalTokens tokens;
    private final ClinicalClient clinical;

    public ClinicalGatewayController(AccessService access, SessionSupport sessions, CredentialingService credentialing, InternalTokens tokens,
            ClinicalClient clinical) {
        this.access = access;
        this.sessions = sessions;
        this.credentialing = credentialing;
        this.tokens = tokens;
        this.clinical = clinical;
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

        // o caminho já passou pela lista de recursos
        return clinical.send(method, path, query, token, request.getHeader("Accept"), request.getContentType(), request.getInputStream().readAllBytes());
    }
}
