package com.doctorexplorer.clinical.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;

/**
 * Medicamentos da lista CMED/Anvisa (PMC de 09/10/2026, sem os de uso hospitalar), pela chave GGREM. A tarja diz o
 * que entra no MVP: tarja preta e venda com retenção de receita são controle especial (Portaria 344) e ficam fora
 * ⚖️; a exceção são os antibacterianos sistêmicos (classe EphMRA J1), que vão em receita simples de 2 vias
 * (RDC 471/2021) com validade de 10 dias. A tarja da CMED varia entre apresentações da mesma substância (há
 * alprazolam "vermelha" e "não informada"), então o controle vale para a substância inteira, e as classes de
 * opioides, antipsicóticos, ansiolíticos e antidepressivos (EphMRA N2A, N5A, N5C, N6A) são sempre controle especial.
 */
@Component
public class MedicationCatalog {

    public enum Tarja { VERMELHA, VERMELHA_RESTRICAO, SEM_TARJA, PRETA, NAO_INFORMADA }

    static final List<String> CONTROLLED_CLASSES = List.of("N2A", "N5A", "N5C", "N6A");

    public record Medication(String ggrem, String registry, String substance, String product, String presentation, String therapeuticClass,
            Tarja tarja, String laboratory, boolean controlledSubstance) {

        public boolean antimicrobial() {
            return therapeuticClass.startsWith("J1");
        }

        /** Controle especial fora do MVP: a substância tem alguma apresentação com tarja preta ou retenção (sem ser antibacteriano), ou a classe é controlada. */
        public boolean specialControl() {
            return controlledSubstance;
        }

        boolean controlledRow() {
            return tarja == Tarja.PRETA || (tarja == Tarja.VERMELHA_RESTRICAO && !antimicrobial())
                    || CONTROLLED_CLASSES.stream().anyMatch(therapeuticClass::startsWith);
        }

        public String label() {
            return substance + " · " + presentation + " (" + product + ")";
        }
    }

    private final Map<String, Medication> byGgrem = new LinkedHashMap<>();
    private final List<Object[]> searchable = new ArrayList<>(); // normalized text, medication

    public MedicationCatalog() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(MedicationCatalog.class.getResourceAsStream("/catalog/cmed.tsv.gz")),
                StandardCharsets.UTF_8))) {
            String line;
            List<Medication> rows = new ArrayList<>();
            java.util.Set<String> controlled = new java.util.HashSet<>();
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] f = line.split("\t", -1);
                Medication m = new Medication(f[0], f[1], f[2], f[3], f[4], f[5], Tarja.valueOf(f[6]), f[7], false);
                rows.add(m);
                if (m.controlledRow()) controlled.add(m.substance());
            }
            for (Medication row : rows) {
                Medication m = new Medication(row.ggrem(), row.registry(), row.substance(), row.product(), row.presentation(), row.therapeuticClass(), row.tarja(),
                        row.laboratory(), !row.antimicrobial() && (row.controlledRow() || controlled.contains(row.substance())));
                byGgrem.put(m.ggrem(), m);
                searchable.add(new Object[] {normalize(m.substance() + " " + m.product() + " " + m.presentation()), m});
            }
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("catálogo CMED ausente", e);
        }
    }

    public int size() {
        return byGgrem.size();
    }

    public Optional<Medication> byGgrem(String ggrem) {
        return Optional.ofNullable(ggrem == null ? null : byGgrem.get(ggrem.trim()));
    }

    /** Todas as palavras (sem acento) precisam aparecer em substância, produto ou apresentação. */
    public List<Medication> search(String query, int limit) {
        String q = query == null ? "" : normalize(query.trim());
        if (q.length() < 3) return List.of();
        String[] words = q.split("\\s+");
        List<Medication> out = new ArrayList<>();
        for (Object[] s : searchable) {
            String text = (String) s[0];
            boolean all = true;
            for (String w : words) if (!text.contains(w)) { all = false; break; }
            if (all) out.add((Medication) s[1]);
            if (out.size() >= limit) break;
        }
        return out;
    }

    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
