package com.doctorexplorer.clinical.auth;

import java.time.Instant;
import java.util.UUID;

/** Quem chama, segundo o token interno de 60 s emitido pela core-api para esta requisição. */
public record Caller(UUID userId, String name, String crm, String uf, String seal, long sudoUntil, boolean mfa, boolean write, String rawToken) {

    public boolean verified() {
        return "VERIFIED".equals(seal);
    }

    /** Token emitido pela core-api para as rotas sem login (sem conta: sub nulo, selo PUBLIC). */
    public boolean isPublic() {
        return "PUBLIC".equals(seal);
    }

    public boolean sudoActive() {
        return sudoUntil > Instant.now().toEpochMilli();
    }
}
