package com.doctorexplorer.clinical.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RF09 · o que entra na receita simples: controle especial vale para a substância inteira, não para a linha da CMED. */
class MedicationCatalogTest {

    static final MedicationCatalog CATALOG = new MedicationCatalog();

    @Test
    void controlledSubstanceIsBlockedInEveryPresentationEvenWhenTheCmedRowSaysOtherwise() {
        var alprazolam = CATALOG.search("alprazolam", 500);
        assertThat(alprazolam).isNotEmpty().anyMatch(m -> m.tarja() != MedicationCatalog.Tarja.PRETA).allMatch(MedicationCatalog.Medication::specialControl);
        assertThat(CATALOG.search("sertralina", 500)).isNotEmpty().allMatch(MedicationCatalog.Medication::specialControl);
        assertThat(CATALOG.search("tramadol", 500)).isNotEmpty().allMatch(MedicationCatalog.Medication::specialControl);
    }

    @Test
    void antibacterialsAndCommonDrugsStayInTheSimplePrescription() {
        // kits para H. pylori (classe A2B2, com retenção) ficam de fora por cautela; a amoxicilina J1 entra
        var amoxicillin = CATALOG.search("amoxicilina", 500).stream().filter(m -> m.therapeuticClass().startsWith("J1")).toList();
        assertThat(amoxicillin).isNotEmpty().allMatch(MedicationCatalog.Medication::antimicrobial).noneMatch(MedicationCatalog.Medication::specialControl);
        assertThat(CATALOG.search("dipirona", 500)).isNotEmpty().noneMatch(MedicationCatalog.Medication::specialControl);
        assertThat(CATALOG.search("losartana", 500)).isNotEmpty().noneMatch(MedicationCatalog.Medication::specialControl);
    }
}
