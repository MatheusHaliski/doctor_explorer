package com.doctorexplorer.core.institution.service;

import java.util.Optional;

/** Portas para a Receita Federal (CNPJ) e o CNES (DATASUS). Fakes em dev/it; HTTP em produção. */
public final class RegistryPorts {

    private RegistryPorts() {}

    public record CnpjRecord(String legalName, String situation, String city, String uf, String raw) {
        public boolean isActive() {
            return "ATIVA".equalsIgnoreCase(situation == null ? "" : situation.trim());
        }
    }

    public record CnesRecord(String name, String cnpj, String city, String uf, String raw) {}

    public interface CnpjPort {
        Optional<CnpjRecord> lookup(String cnpj);

        String sourceName();
    }

    public interface CnesPort {
        Optional<CnesRecord> lookupCnes(String cnes);

        String cnesSourceName();
    }

    public static class RegistryUnavailableException extends RuntimeException {
        public RegistryUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
