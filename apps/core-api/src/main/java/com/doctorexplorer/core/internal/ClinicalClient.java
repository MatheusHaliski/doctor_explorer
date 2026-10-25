package com.doctorexplorer.core.internal;

import com.doctorexplorer.core.web.ApiProblemException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Chamada à clinical-api com o token interno. O caminho chega já conferido pelo chamador; esquema, host e porta vêm
 * só da configuração. Só alguns cabeçalhos da resposta voltam, e nunca em cache.
 */
@Component
public class ClinicalClient {

    private static final Set<String> FORWARDED_RESPONSE = Set.of("content-type", "content-disposition", "etag", "last-modified", "location");

    private final ClinicalProperties props;
    private final HttpClient http;

    public ClinicalClient(ClinicalProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder().connectTimeout(props.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public ResponseEntity<byte[]> send(String method, String path, String query, String token, String accept, String contentType, byte[] body) {
        URI base = URI.create(props.baseUrl());
        URI target = URI.create(base.getScheme() + "://" + base.getRawAuthority() + path + (query == null ? "" : "?" + query));
        HttpRequest.Builder b = HttpRequest.newBuilder(target).timeout(props.timeout())
                .header("Authorization", "DE-Internal " + token)
                .header("Accept", accept == null ? "application/json" : accept)
                .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null && body.length > 0) b.header("Content-Type", contentType);
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
