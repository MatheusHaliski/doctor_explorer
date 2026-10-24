package com.doctorexplorer.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class AccountStatusTest {

    @ParameterizedTest
    @CsvSource({
        "PENDING_VERIFICATION, ACTIVE",
        "PENDING_VERIFICATION, ANONYMOUS_LGPD_DELETED",
        "ACTIVE, SUSPENDED",
        "ACTIVE, PENDING_DELETION",
        "SUSPENDED, ACTIVE",
        "PENDING_DELETION, ACTIVE",
        "PENDING_DELETION, ANONYMOUS_LGPD_DELETED",
    })
    void allowsTheTransitionsOfTheStateMachine(AccountStatus from, AccountStatus to) {
        assertThat(from.transitionTo(to)).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING_VERIFICATION, SUSPENDED",
        "PENDING_VERIFICATION, PENDING_DELETION",
        "ACTIVE, ANONYMOUS_LGPD_DELETED",
        "ACTIVE, PENDING_VERIFICATION",
        "SUSPENDED, PENDING_DELETION",
        "PENDING_DELETION, SUSPENDED",
    })
    void rejectsTransitionsOutsideTheStateMachine(AccountStatus from, AccountStatus to) {
        assertThatThrownBy(() -> from.transitionTo(to))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void deletedIsTerminalAndNothingLeavesIt(AccountStatus target) {
        assertThat(AccountStatus.ANONYMOUS_LGPD_DELETED.canTransitionTo(target)).isFalse();
        assertThat(AccountStatus.ANONYMOUS_LGPD_DELETED.isTerminal()).isTrue();
    }

    @Test
    void sealOnlyVerifiedGrantsWrite() {
        for (SealStatus s : SealStatus.values()) {
            assertThat(s.grantsClinicalAndSocialWrite()).isEqualTo(s == SealStatus.VERIFIED);
        }
        assertThat(SealStatus.VERIFIED.transitionTo(SealStatus.PENDING)).isEqualTo(SealStatus.PENDING);
        assertThatThrownBy(() -> SealStatus.NONE.transitionTo(SealStatus.VERIFIED)).isInstanceOf(InvalidTransitionException.class);
    }
}
