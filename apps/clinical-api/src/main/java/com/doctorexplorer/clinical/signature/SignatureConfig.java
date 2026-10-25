package com.doctorexplorer.clinical.signature;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Escolha do provedor (doctor-explorer.signature.provider): {@code simulated} em dev e nos testes (AC e carimbo do
 * tempo de teste, marcados como sem validade jurídica), {@code unavailable} por padrão. O adaptador de um provedor
 * homologado entra aqui quando houver contrato (ADR-007).
 */
@Configuration
public class SignatureConfig {

    @Bean
    SignatureProviderPort signatureProvider(@Value("${doctor-explorer.signature.provider:unavailable}") String provider, ClinicalProperties props) {
        return switch (provider) {
            case "simulated" -> new SimulatedIcpProvider(props);
            case "unavailable" -> new UnavailableSignatureProvider();
            default -> throw new IllegalStateException("doctor-explorer.signature.provider desconhecido: " + provider);
        };
    }
}
