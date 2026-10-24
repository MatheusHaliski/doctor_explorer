package com.doctorexplorer.core.identity.domain;

import java.util.EnumSet;
import java.util.Set;

/** Selo profissional (RF02). Só {@code VERIFIED} libera publicar, conversar, chamar, prescrever e o diretório. */
public enum SealStatus {
    NONE,
    PENDING,
    MANUAL_REVIEW,
    VERIFIED,
    REJECTED,
    REVOKED;

    public Set<SealStatus> allowedTransitions() {
        return switch (this) {
            case NONE -> EnumSet.of(PENDING);
            case PENDING -> EnumSet.of(VERIFIED, REJECTED, MANUAL_REVIEW, PENDING);
            case MANUAL_REVIEW -> EnumSet.of(VERIFIED, REJECTED);
            case VERIFIED -> EnumSet.of(PENDING, REVOKED);
            case REJECTED -> EnumSet.of(PENDING);
            case REVOKED -> EnumSet.of(PENDING);
        };
    }

    public SealStatus transitionTo(SealStatus target) {
        if (!allowedTransitions().contains(target)) {
            throw new InvalidTransitionException(this, target);
        }
        return target;
    }

    public boolean grantsClinicalAndSocialWrite() {
        return this == VERIFIED;
    }
}
