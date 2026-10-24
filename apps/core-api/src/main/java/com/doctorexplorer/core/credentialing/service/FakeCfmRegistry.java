package com.doctorexplorer.core.credentialing.service;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fonte simulada do CFM para dev/it, determinística pelos dois primeiros dígitos do CRM (o resto é livre,
 * para cada teste usar um número único):
 * <ul>
 *   <li>{@code 12xxxx}: Regular, "Ana Ribeiro", RQE 11111 (UTI) e 22222 (CLIN)</li>
 *   <li>{@code 65xxxx}: Regular, "Bruno Tavares", RQE 33333 (CARDIO)</li>
 *   <li>{@code 99xxxx}: Suspenso</li>
 *   <li>{@code 00xxxx}: fonte indisponível (timeout)</li>
 *   <li>qualquer outro: não encontrado</li>
 * </ul>
 */
@Component
@Profile({"dev", "it"})
public class FakeCfmRegistry implements CfmRegistryPort {

    @Override
    public Optional<CfmRecord> lookup(String crmNumber, String crmUf) {
        String prefix = crmNumber.length() >= 2 ? crmNumber.substring(0, 2) : "";
        return switch (prefix) {
            case "12" -> Optional.of(new CfmRecord("Ana Ribeiro", "Regular",
                    List.of(new Specialty("MEDICINA INTENSIVA", "11111"), new Specialty("CLINICA MEDICA", "22222")), "fake:" + crmNumber));
            case "65" -> Optional.of(new CfmRecord("Bruno Tavares", "Regular", List.of(new Specialty("CARDIOLOGIA", "33333")), "fake:" + crmNumber));
            case "99" -> Optional.of(new CfmRecord("Carlos Suspenso", "Suspenso", List.of(), "fake:" + crmNumber));
            case "00" -> throw new RegistryUnavailableException("fonte simulada indisponível", null);
            default -> Optional.empty();
        };
    }

    @Override
    public String sourceName() {
        return "cfm-fake";
    }
}
