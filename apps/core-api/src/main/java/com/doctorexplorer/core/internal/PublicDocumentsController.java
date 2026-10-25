package com.doctorexplorer.core.internal;

import com.doctorexplorer.core.identity.service.RequestContext;
import com.doctorexplorer.core.web.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF09 · rotas sem login: o validador da farmácia (código do QR) e o link de uso único do paciente. Seguem para a
 * clinical-api com o token PUBLIC, que não abre nenhuma outra rota de lá. O link do paciente é POST (com CSRF) de
 * propósito: o antivírus do e-mail que "abre" o link com GET não gasta o uso único.
 */
@RestController
public class PublicDocumentsController {

    static final Pattern CODE = Pattern.compile("^[A-Za-z0-9-]{16,19}$");
    static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{20,64}$");
    static final int MAX_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(10);
    static final UUID NOBODY = new UUID(0, 0);

    private final InternalTokens tokens;
    private final ClinicalClient clinical;
    private final RequestContext context;
    private final StringRedisTemplate redis;

    public PublicDocumentsController(InternalTokens tokens, ClinicalClient clinical, RequestContext context, StringRedisTemplate redis) {
        this.tokens = tokens;
        this.clinical = clinical;
        this.context = context;
        this.redis = redis;
    }

    @GetMapping("/api/v1/public/documents/{code}")
    ResponseEntity<byte[]> validate(@PathVariable String code, HttpServletRequest request) {
        limit("validate", request);
        if (!CODE.matcher(code).matches()) throw notFound();
        return clinical.send("GET", "/public/documents/" + code, null, publicToken(), "application/json", null, new byte[0]);
    }

    @PostMapping("/api/v1/public/deliveries/{token}")
    ResponseEntity<byte[]> open(@PathVariable String token, HttpServletRequest request) {
        limit("delivery", request);
        if (!TOKEN.matcher(token).matches()) throw new ApiProblemException(HttpStatus.GONE, "link-indisponivel", "Link indisponível",
                "Este link já foi usado ou passou de 7 dias. Peça ao médico um novo envio.");
        return clinical.send("GET", "/public/deliveries/" + token, null, publicToken(), "application/pdf, application/problem+json", null, new byte[0]);
    }

    private String publicToken() {
        return tokens.issue(NOBODY, "", null, null, "PUBLIC", 0, false, false, InternalTokens.CLINICAL);
    }

    private void limit(String route, HttpServletRequest request) {
        String key = "rl:public:" + route + ":" + context.ipHash(request);
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) redis.expire(key, WINDOW);
        if (n != null && n > MAX_PER_IP) {
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "muitas-consultas", "Muitas consultas",
                    "Aguarde alguns minutos antes de consultar de novo.");
        }
    }

    private static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "codigo-invalido", "Código não encontrado",
                "Confira o código impresso no documento.");
    }
}
