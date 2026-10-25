package com.doctorexplorer.clinical.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Cid10Test {

    private final Cid10 cid10 = new Cid10();

    @Test
    void loadsTheFullDatasusTable() {
        assertThat(cid10.size()).isGreaterThan(14_000);
        assertThat(cid10.lookup("j189")).hasValueSatisfying(c -> assertThat(c.code()).isEqualTo("J18.9"));
        assertThat(cid10.lookup("I21")).hasValueSatisfying(c -> assertThat(c.display()).isEqualTo("Infarto agudo do miocárdio"));
        assertThat(cid10.lookup("Z99.99")).isEmpty();
    }

    @Test
    void searchesByCodePrefixOrWordsWithoutAccents() {
        assertThat(cid10.search("A41", 20)).extracting(Cid10.Code::code).contains("A41", "A41.9");
        assertThat(cid10.search("septicemia nao especificada", 20)).extracting(Cid10.Code::code).contains("A41.9");
        assertThat(cid10.search("x", 20)).isEmpty();
    }
}
