package com.doctorexplorer.core.community.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * RF04 · Bloqueia texto com identificador de paciente antes de publicar: CPF, CNS, telefone, e-mail, data
 * completa de nascimento/atendimento, número de prontuário e "paciente Nome Sobrenome". Regras por
 * expressão regular, deliberadamente conservadoras (melhor um falso positivo que um vazamento); o NER em
 * pt-BR entra como segunda camada (ADR futuro).
 */
@Component
public class IdentifierDetector {

    public record Match(String type, String excerpt, String hint) {}

    private record Rule(String type, Pattern pattern, String hint) {}

    private static final List<Rule> RULES = List.of(
            new Rule("CPF", Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b"), "Remova o CPF."),
            new Rule("CNS", Pattern.compile("\\b[1-2]\\d{14}\\b|\\b[7-9]\\d{14}\\b"), "Remova o número do Cartão Nacional de Saúde."),
            new Rule("TELEFONE", Pattern.compile("(?<!\\d)(?:\\+55\\s?)?\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}(?!\\d)"), "Remova o telefone."),
            new Rule("EMAIL", Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+"), "Remova o e-mail."),
            new Rule("DATA_COMPLETA", Pattern.compile("\\b\\d{1,2}/\\d{1,2}/(?:19|20)\\d{2}\\b"), "Troque a data completa por idade ou intervalo (ex.: \"há 3 semanas\")."),
            new Rule("PRONTUARIO", Pattern.compile("(?i)\\b(?:prontu[áa]rio|registro|atendimento|RH|RG)\\s*(?:n[ºo°.]?\\s*)?[:#]?\\s*\\d{4,}\\b"), "Remova o número de prontuário/registro."),
            new Rule("NOME_PACIENTE", Pattern.compile("\\b(?i:paciente|sr\\.?|sra\\.?|senhor|senhora|dona)\\s+(\\p{Lu}\\p{Ll}+(?:\\s+(?:d[aeo]s?\\s+)?\\p{Lu}\\p{Ll}+)+)"), "Não cite o nome do paciente; use idade e sexo."));

    public List<Match> scan(String text) {
        List<Match> matches = new ArrayList<>();
        if (text == null || text.isBlank()) return matches;
        for (Rule r : RULES) {
            Matcher m = r.pattern().matcher(text);
            while (m.find()) {
                matches.add(new Match(r.type(), excerpt(text, m.start(), m.end()), r.hint()));
            }
        }
        return matches;
    }

    private static String excerpt(String text, int start, int end) {
        int a = Math.max(0, start - 15);
        int b = Math.min(text.length(), end + 15);
        return (a > 0 ? "…" : "") + text.substring(a, b).replace("\n", " ") + (b < text.length() ? "…" : "");
    }
}
