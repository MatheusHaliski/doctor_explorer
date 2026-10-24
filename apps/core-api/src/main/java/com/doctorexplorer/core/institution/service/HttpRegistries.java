package com.doctorexplorer.core.institution.service;

import com.doctorexplorer.core.institution.service.RegistryPorts.CnesPort;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnesRecord;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnpjPort;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnpjRecord;
import com.doctorexplorer.core.institution.service.RegistryPorts.RegistryUnavailableException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Adapters HTTP: CNPJ pela BrasilAPI (dados públicos da Receita) e CNES pela API de dados abertos do
 * DATASUS. Campos conforme as respostas públicas em 2026; qualquer mudança fica isolada aqui.
 */
@Component
@Profile("!dev & !it")
public class HttpRegistries implements CnpjPort, CnesPort {

    private final RestClient cnpjClient;
    private final RestClient cnesClient;

    public HttpRegistries(@Value("${doctor-explorer.registries.cnpj-base-url:https://brasilapi.com.br/api/cnpj/v1}") String cnpjBase,
            @Value("${doctor-explorer.registries.cnes-base-url:https://apidadosabertos.saude.gov.br/cnes/estabelecimentos}") String cnesBase) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory();
        f.setReadTimeout(Duration.ofSeconds(8));
        this.cnpjClient = RestClient.builder().baseUrl(cnpjBase).requestFactory(f).build();
        this.cnesClient = RestClient.builder().baseUrl(cnesBase).requestFactory(f).build();
    }

    @Override
    public Optional<CnpjRecord> lookup(String cnpj) {
        try {
            Map<String, Object> b = cnpjClient.get().uri("/{cnpj}", cnpj).retrieve().body(Map.class);
            if (b == null) return Optional.empty();
            return Optional.of(new CnpjRecord(str(b.get("razao_social")), str(b.get("descricao_situacao_cadastral")), str(b.get("municipio")), str(b.get("uf")), b.toString()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new RegistryUnavailableException("Receita (BrasilAPI) indisponível", e);
        }
    }

    @Override
    public Optional<CnesRecord> lookupCnes(String cnes) {
        try {
            Map<String, Object> b = cnesClient.get().uri("/{cnes}", cnes).retrieve().body(Map.class);
            if (b == null) return Optional.empty();
            return Optional.of(new CnesRecord(str(b.get("nome_fantasia")), str(b.get("numero_cnpj")), str(b.get("codigo_municipio")), str(b.get("codigo_uf")), b.toString()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new RegistryUnavailableException("CNES (DATASUS) indisponível", e);
        }
    }

    @Override
    public String cnesSourceName() {
        return sourceName() + "-cnes";
    }

    @Override
    public String sourceName() {
        return "brasilapi+datasus";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
