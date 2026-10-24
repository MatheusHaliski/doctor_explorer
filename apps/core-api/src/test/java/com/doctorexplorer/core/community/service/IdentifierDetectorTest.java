package com.doctorexplorer.core.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IdentifierDetectorTest {

    IdentifierDetector detector = new IdentifierDetector();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "CPF 123.456.789-09 do paciente | CPF",
        "cpf 12345678909 | CPF",
        "CNS 898001234567890 | CNS",
        "ligar para (41) 99876-5432 | TELEFONE",
        "contato 41998765432 | TELEFONE",
        "mãe: maria@example.org | EMAIL",
        "internado em 12/03/2024 | DATA_COMPLETA",
        "prontuário nº 123456 | PRONTUARIO",
        "Registro: 987654 | PRONTUARIO",
        "paciente João da Silva, 60 anos | NOME_PACIENTE",
        "a Sra. Maria Aparecida Souza chegou | NOME_PACIENTE",
    })
    void flagsPatientIdentifiers(String text, String type) {
        assertThat(detector.scan(text)).extracting(IdentifierDetector.Match::type).contains(type);
    }

    @Test
    void acceptsAnonymizedClinicalText() {
        String ok = "Mulher, 60 anos, choque séptico refratário há 36 h. Lactato 4,2 → 3,1. Noradrenalina 0,8 mcg/kg/min. Culturas negativas até agora. Alguém usaria corticoide neste ponto?";
        assertThat(detector.scan(ok)).isEmpty();
        assertThat(detector.scan("PA 120/80, FC 98, SatO2 94% em 2 L/min; evolução de 3 semanas")).isEmpty();
        assertThat(detector.scan(null)).isEmpty();
    }

    @Test
    void excerptShowsContextWithoutTheWholeText() {
        var m = detector.scan("história longa aqui e o CPF 123.456.789-09 aparece no meio de um parágrafo grande").get(0);
        assertThat(m.excerpt()).contains("123.456.789-09").startsWith("…").endsWith("…");
        assertThat(m.hint()).contains("CPF");
    }
}
