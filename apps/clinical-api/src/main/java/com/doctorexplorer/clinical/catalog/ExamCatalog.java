package com.doctorexplorer.clinical.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Exames do MVP por LOINC, cada um com o código TUSS 22 equivalente (o que o convênio e o laboratório usam). */
@Component
public class ExamCatalog {

    public static final String LOINC = "http://loinc.org";
    public static final String TUSS = "https://www.gov.br/ans/tuss/tabela-22";

    public record Exam(String loinc, String name, String tuss, String tussTerm) {}

    private final Map<String, Exam> byLoinc = new LinkedHashMap<>();

    public ExamCatalog() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ExamCatalog.class.getResourceAsStream("/catalog/exams.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] f = line.split("\t", -1);
                byLoinc.put(f[0], new Exam(f[0], f[1], f[2], f[3]));
            }
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("catálogo de exames ausente", e);
        }
    }

    public Optional<Exam> byLoinc(String loinc) {
        return Optional.ofNullable(loinc == null ? null : byLoinc.get(loinc.trim()));
    }

    public List<Exam> search(String query, int limit) {
        String q = query == null ? "" : MedicationCatalog.normalize(query.trim());
        List<Exam> out = new ArrayList<>();
        for (Exam e : byLoinc.values()) {
            if (q.isEmpty() || MedicationCatalog.normalize(e.name() + " " + e.tussTerm() + " " + e.loinc() + " " + e.tuss()).contains(q)) out.add(e);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public int size() {
        return byLoinc.size();
    }
}
