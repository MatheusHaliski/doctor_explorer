package com.doctorexplorer.core.credentialing.service;

import java.util.List;
import java.util.Optional;

/**
 * Porta para a fonte oficial do CFM (ADR-009). A implementação HTTP depende das condições de uso do
 * serviço do CFM (⚖️ Q04); em dev/it vale o {@link FakeCfmRegistry}.
 */
public interface CfmRegistryPort {

    record Specialty(String name, String rqe) {}

    /** Registro público do médico: nome, situação ("Regular", "Suspenso"…) e especialidades com RQE. */
    record CfmRecord(String name, String situation, List<Specialty> specialties, String rawResponse) {
        public boolean isRegular() {
            return "REGULAR".equalsIgnoreCase(situation == null ? "" : situation.trim());
        }
    }

    /** Lança {@link RegistryUnavailableException} quando a fonte não responde. Vazio = CRM não encontrado. */
    Optional<CfmRecord> lookup(String crmNumber, String crmUf);

    String sourceName();

    class RegistryUnavailableException extends RuntimeException {
        public RegistryUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
