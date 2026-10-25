package com.doctorexplorer.clinical.audit;

import com.doctorexplorer.clinical.auth.Caller;
import com.doctorexplorer.clinical.config.ClinicalProperties;
import com.doctorexplorer.clinical.web.ApiProblemException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * RF08 critério 7: toda leitura e escrita vira evento na auditoria encadeada da core-api (RF13), com quem, quando,
 * qual paciente (id opaco do cofre) e o quê. Usa o mesmo token interno da chamada; se a auditoria falhar, a operação
 * falha junto (dentro da transação, a escrita é desfeita) — nada clínico acontece sem registro.
 */
@Component
public class CoreAudit {

    private final ClinicalProperties props;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public CoreAudit(ClinicalProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder().connectTimeout(props.core().timeout()).build();
    }

    public void record(Caller caller, String action, String resourceType, UUID resourceId, UUID patientId, Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("resourceType", resourceType);
        body.put("resourceId", resourceId == null ? "" : resourceId.toString());
        body.put("patientRef", patientId == null ? null : patientId.toString());
        body.put("outcome", "success");
        Map<String, String> d = new LinkedHashMap<>();
        if (details != null) details.forEach((k, v) -> d.put(k, String.valueOf(v)));
        body.put("details", d);
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(props.core().baseUrl() + "/api/v1/internal/audit"))
                    .timeout(props.core().timeout()).header("Content-Type", "application/json").header("Authorization", "DE-Internal " + caller.rawToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 201) throw unavailable();
        } catch (java.io.IOException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static ApiProblemException unavailable() {
        return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "auditoria-indisponivel", "Auditoria indisponível",
                "Sem registro na auditoria o prontuário não é lido nem alterado. Tente de novo em instantes.");
    }
}
