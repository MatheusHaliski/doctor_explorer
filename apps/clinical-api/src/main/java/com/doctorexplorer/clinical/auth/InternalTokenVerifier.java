package com.doctorexplorer.clinical.auth;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Confere o token interno ({@code v1.<payload>.<Ed25519>}, ADR-008): assinatura com a chave pública da core-api,
 * público "clinical-api", validade (60 s) e emissão sem relógio adiantado. A chave vem da configuração ou é
 * buscada uma vez em {@code GET /api/v1/internal/public-key} da core-api.
 */
@Component
public class InternalTokenVerifier {

    static final long CLOCK_SKEW_MS = 5_000;

    private final ClinicalProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private volatile PublicKey key;

    public InternalTokenVerifier(ClinicalProperties props) {
        this.props = props;
    }

    public Optional<Caller> verify(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !"v1".equals(parts[0])) return Optional.empty();
        try {
            Signature v = Signature.getInstance("Ed25519");
            v.initVerify(key());
            v.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!v.verify(Base64.getUrlDecoder().decode(parts[2]))) return Optional.empty();
            Map<?, ?> c = json.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            long now = System.currentTimeMillis();
            long exp = ((Number) c.get("exp")).longValue();
            long iat = ((Number) c.get("iat")).longValue();
            if (!"clinical-api".equals(c.get("aud")) || !"core-api".equals(c.get("iss")) || exp < now || iat > now + CLOCK_SKEW_MS) return Optional.empty();
            return Optional.of(new Caller(UUID.fromString((String) c.get("sub")), (String) c.get("name"), (String) c.get("crm"), (String) c.get("uf"),
                    (String) c.get("seal"), ((Number) c.get("sudoUntil")).longValue(), Boolean.TRUE.equals(c.get("mfa")), Boolean.TRUE.equals(c.get("write")), token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private PublicKey key() throws Exception {
        if (key == null) {
            synchronized (this) {
                if (key == null) {
                    String spki = props.core().publicKey();
                    if (spki == null || spki.isBlank()) spki = fetch();
                    key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(spki.trim())));
                }
            }
        }
        return key;
    }

    private String fetch() throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(props.core().timeout()).build();
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(props.core().baseUrl() + "/api/v1/internal/public-key"))
                .timeout(props.core().timeout()).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("chave pública da core-api indisponível: " + res.statusCode());
        return (String) json.readValue(res.body(), Map.class).get("spki");
    }
}
