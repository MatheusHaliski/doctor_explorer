package com.doctorexplorer.core.identity.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados da conta (docs/MAQUINA_ESTADOS.md). Transição inválida lança
 * {@link InvalidTransitionException}; {@code ANONYMOUS_LGPD_DELETED} é terminal.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    PENDING_DELETION,
    ANONYMOUS_LGPD_DELETED;

    public Set<AccountStatus> allowedTransitions() {
        return switch (this) {
            case PENDING_VERIFICATION -> EnumSet.of(ACTIVE, ANONYMOUS_LGPD_DELETED);
            case ACTIVE -> EnumSet.of(SUSPENDED, PENDING_DELETION);
            case SUSPENDED -> EnumSet.of(ACTIVE);
            case PENDING_DELETION -> EnumSet.of(ACTIVE, ANONYMOUS_LGPD_DELETED);
            case ANONYMOUS_LGPD_DELETED -> EnumSet.noneOf(AccountStatus.class);
        };
    }

    public boolean canTransitionTo(AccountStatus target) {
        return allowedTransitions().contains(target);
    }

    public AccountStatus transitionTo(AccountStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidTransitionException(this, target);
        }
        return target;
    }

    public boolean isTerminal() {
        return this == ANONYMOUS_LGPD_DELETED;
    }
}
