package com.doctorexplorer.core.credentialing.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Adapter HTTP para o serviço de consulta do CFM (ADR-009). O contrato exato (URL, autenticação, campos)
 * depende do convênio com o CFM (⚖️ Q04); este adapter espera um JSON {@code {nome, situacao,
 * especialidades:[{nome, rqe}]}} em {@code GET {base}/medicos/{uf}/{crm}} e é trocado sem tocar no serviço.
 */
@Component
@Profile("!dev & !it")
public class HttpCfmRegistry implements CfmRegistryPort {

    private final RestClient client;

    public HttpCfmRegistry(@Value("${doctor-explorer.cfm.base-url:https://cfm.invalid}") String baseUrl,
            @Value("${doctor-explorer.cfm.api-key:}") String apiKey) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<CfmRecord> lookup(String crmNumber, String crmUf) {
        try {
            Map<String, Object> body = client.get().uri("/medicos/{uf}/{crm}", crmUf, crmNumber).retrieve().body(Map.class);
            if (body == null) return Optional.empty();
            List<Map<String, Object>> esp = (List<Map<String, Object>>) body.getOrDefault("especialidades", List.of());
            return Optional.of(new CfmRecord(String.valueOf(body.get("nome")), String.valueOf(body.get("situacao")),
                    esp.stream().map(e -> new Specialty(String.valueOf(e.get("nome")), String.valueOf(e.get("rqe")))).toList(),
                    body.toString()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new RegistryUnavailableException("Fonte do CFM indisponível", e);
        }
    }

    @Override
    public String sourceName() {
        return "cfm-http";
    }
}
