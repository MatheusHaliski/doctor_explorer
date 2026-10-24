package com.doctorexplorer.core.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * RF03 · Publicidade médica (Res. CFM 2.336/2023): sem promessa de resultado, sem "antes e depois", sem
 * superlativos de autopromoção, sem preço como chamariz. Lista inicial, conservadora; o moderador decide
 * os casos limítrofes.
 */
@Component
public class AdvertisingRuleChecker {

    private record Rule(Pattern pattern, String message) {}

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)\\b(garant\\w*|resultado garantido|cura garantida|100% de sucesso|sem risco)\\b"), "Não prometa resultado nem garantia (Res. CFM 2.336/2023, art. 3º)."),
            new Rule(Pattern.compile("(?i)\\bantes e depois\\b"), "Não use \"antes e depois\" de pacientes."),
            new Rule(Pattern.compile("(?i)\\b(o melhor|a melhor|o único|a única|número 1|n[º°] ?1|referência nacional|mais bem avaliado)\\b"), "Evite superlativos de autopromoção."),
            new Rule(Pattern.compile("(?i)(R\\$\\s?\\d|\\bpromo[çc][aã]o\\b|\\bdesconto\\b|\\bparcel\\w+)"), "Preço, promoção ou desconto não podem ser chamariz no perfil."),
            new Rule(Pattern.compile("(?i)\\b(milagr\\w*|revolucion[áa]ri\\w*|infal[íi]vel)\\b"), "Evite termos sensacionalistas."));

    public List<String> check(String text) {
        List<String> findings = new ArrayList<>();
        if (text == null || text.isBlank()) return findings;
        for (Rule r : RULES) {
            if (r.pattern().matcher(text).find()) findings.add(r.message());
        }
        return findings;
    }
}
