package com.doctorexplorer.clinical.terminology;

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
import org.springframework.stereotype.Component;

/**
 * CID-10 (tabela do DATASUS, categorias e subcategorias) embarcada: valida o código da avaliação e alimenta o
 * autocompletar. SNOMED CT entra como código opcional (formato); o servidor de terminologia completo é ADR-005.
 */
@Component
public class Cid10 {

    public static final String SYSTEM = "http://www.saude.gov.br/fhir/r4/CodeSystem/BRCID10";

    public record Code(String code, String display) {}

    private final Map<String, String> byCode = new LinkedHashMap<>();
    private final List<String[]> searchable = new ArrayList<>(); // code, normalized display

    public Cid10() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(Cid10.class.getResourceAsStream("/terminology/cid10.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                int tab = line.indexOf('\t');
                String code = line.substring(0, tab);
                String display = line.substring(tab + 1);
                byCode.put(code, display);
                searchable.add(new String[] {code, normalize(display)});
            }
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("tabela CID-10 ausente", e);
        }
    }

    public int size() {
        return byCode.size();
    }

    /** Aceita "J18.9", "j189" ou "J18" e devolve o código canônico com ponto. */
    public Optional<Code> lookup(String raw) {
        if (raw == null) return Optional.empty();
        String c = raw.trim().toUpperCase(Locale.ROOT).replace(".", "");
        if (c.length() > 3) c = c.substring(0, 3) + "." + c.substring(3);
        String display = byCode.get(c);
        return display == null ? Optional.empty() : Optional.of(new Code(c, display));
    }

    public List<Code> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        List<Code> out = new ArrayList<>();
        if (q.length() < 2) return out;
        if (q.matches("(?i)[a-z]\\d.*")) {
            String prefix = q.toUpperCase(Locale.ROOT).replace(".", "");
            for (Map.Entry<String, String> e : byCode.entrySet()) {
                if (e.getKey().replace(".", "").startsWith(prefix)) out.add(new Code(e.getKey(), e.getValue()));
                if (out.size() >= limit) break;
            }
            return out;
        }
        String[] words = normalize(q).split("\\s+");
        for (String[] s : searchable) {
            boolean all = true;
            for (String w : words) if (!s[1].contains(w)) { all = false; break; }
            if (all) out.add(new Code(s[0], byCode.get(s[0])));
            if (out.size() >= limit) break;
        }
        return out;
    }

    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
