package com.doctorexplorer.core.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class KAnonymityGuardTest {

    @Test
    void suppressesCellsBelowTenPeopleWithoutLeakingTheCount() {
        Map<String, Object> small = KAnonymityGuard.cell(42, 9);
        assertThat(small).containsEntry("suppressed", true).containsEntry("value", null).doesNotContainKey("people");
        assertThat(KAnonymityGuard.cell(0, 0)).containsEntry("suppressed", true).containsEntry("value", null);
        assertThat(KAnonymityGuard.cell(12, 10)).containsEntry("suppressed", false).containsEntry("value", 12);
        assertThat(KAnonymityGuard.MIN_CELL).isEqualTo(10);
    }
}
