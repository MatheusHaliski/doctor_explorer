package com.doctorexplorer.core.institution.service;

import com.doctorexplorer.core.institution.service.RegistryPorts.CnesPort;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnesRecord;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnpjPort;
import com.doctorexplorer.core.institution.service.RegistryPorts.CnpjRecord;
import com.doctorexplorer.core.institution.service.RegistryPorts.RegistryUnavailableException;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fontes simuladas para dev/it, determinísticas pelos dois primeiros dígitos:
 * CNPJ {@code 12…}: ATIVA, "Hospital Santa Clara Ltda", Curitiba/PR · {@code 99…}: BAIXADA · {@code 00…}: indisponível · outro: não encontrado.
 * CNES {@code 12…}: estabelecimento com o mesmo CNPJ informado no cadastro · {@code 99…}: CNPJ diferente · outro: não encontrado.
 */
@Component
@Profile({"dev", "it"})
public class FakeRegistries implements CnpjPort, CnesPort {

    @Override
    public Optional<CnpjRecord> lookup(String cnpj) {
        return switch (prefix(cnpj)) {
            case "12" -> Optional.of(new CnpjRecord("HOSPITAL SANTA CLARA LTDA", "ATIVA", "Curitiba", "PR", "fake-cnpj:" + cnpj));
            case "99" -> Optional.of(new CnpjRecord("CLINICA ENCERRADA LTDA", "BAIXADA", "Curitiba", "PR", "fake-cnpj:" + cnpj));
            case "00" -> throw new RegistryUnavailableException("Receita simulada indisponível", null);
            default -> Optional.empty();
        };
    }

    /** O fake de CNES devolve o CNPJ "12" + os 12 dígitos seguintes do próprio CNES, para o cruzamento bater quando o cadastro é coerente. */
    @Override
    public Optional<CnesRecord> lookupCnes(String cnes) {
        return switch (prefix(cnes)) {
            case "12" -> Optional.of(new CnesRecord("HOSPITAL SANTA CLARA", "12" + cnes.substring(2).concat("000000000000").substring(0, 12), "Curitiba", "PR", "fake-cnes:" + cnes));
            case "99" -> Optional.of(new CnesRecord("OUTRO ESTABELECIMENTO", "99999999000199", "Curitiba", "PR", "fake-cnes:" + cnes));
            case "00" -> throw new RegistryUnavailableException("CNES simulado indisponível", null);
            default -> Optional.empty();
        };
    }

    @Override
    public String cnesSourceName() {
        return sourceName() + "-cnes";
    }

    @Override
    public String sourceName() {
        return "fake";
    }

    private static String prefix(String s) {
        return s != null && s.length() >= 2 ? s.substring(0, 2) : "";
    }
}
