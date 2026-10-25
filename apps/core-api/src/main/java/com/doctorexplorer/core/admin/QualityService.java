package com.doctorexplorer.core.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * RF13 · qualidade: selos pendentes e tempo de verificação (RF02), incidentes de privacidade abertos (RF10),
 * problemas de usabilidade por severidade (planilha em docs/usabilidade, empacotada no classpath) e SLI de
 * disponibilidade e p95 desta instância.
 */
@Service
public class QualityService {

    private final NamedParameterJdbcTemplate jdbc;
    private final SliMetrics sli;

    public QualityService(NamedParameterJdbcTemplate jdbc, SliMetrics sli) {
        this.jdbc = jdbc;
        this.sli = sli;
    }

    public Map<String, Object> quality(Set<UUID> members, boolean includePrivacy) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("members", members == null || members.isEmpty() ? List.of(new UUID(0, 0)) : List.copyOf(members))
                .addValue("all", members == null)
                .addValue("since", Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS)));
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> seals = new LinkedHashMap<>();
        seals.put("pending", jdbc.queryForObject("select count(*) from professional_profile where seal_status = 'PENDING' and (:all or user_id in (:members))", p, Long.class));
        seals.put("manualReview", jdbc.queryForObject("select count(*) from professional_profile where seal_status = 'MANUAL_REVIEW' and (:all or user_id in (:members))", p, Long.class));
        Number avg = jdbc.queryForObject("""
                select avg(extract(epoch from v.first_verified - f.first_check)) / 3600.0
                from (select user_id, min(checked_at) as first_verified from credential_check where outcome = 'VERIFIED' and checked_at >= :since group by user_id) v
                join (select user_id, min(checked_at) as first_check from credential_check group by user_id) f on f.user_id = v.user_id
                where (:all or v.user_id in (:members))""", p, Number.class);
        seals.put("avgHoursToVerify", avg == null ? null : Math.round(avg.doubleValue() * 10) / 10.0);
        out.put("seals", seals);
        if (includePrivacy) {
            Map<String, Object> privacy = new LinkedHashMap<>();
            privacy.put("openIncidents", jdbc.queryForObject("select count(*) from dpo_ticket where type = 'INCIDENT' and state <> 'ANSWERED'", p, Long.class));
            privacy.put("openRequests", jdbc.queryForObject("select count(*) from dpo_ticket where state <> 'ANSWERED'", p, Long.class));
            privacy.put("overdueRequests", jdbc.queryForObject("select count(*) from dpo_ticket where state <> 'ANSWERED' and due_at < now()", p, Long.class));
            out.put("privacy", privacy);
        }
        out.put("usability", usability());
        Map<String, Object> slo = new LinkedHashMap<>();
        slo.put("availability", sli.availability());
        slo.put("p95Millis", sli.p95Millis());
        slo.put("requests", sli.requests());
        slo.put("source", "esta instância desde a subida (produção: OpenTelemetry)");
        out.put("slo", slo);
        return out;
    }

    /** Lê a planilha mais recente (docs/usabilidade/AVALIACAO_*.md) e conta problemas abertos por severidade arredondada. */
    Map<String, Object> usability() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:usabilidade/AVALIACAO_*.md");
            Resource latest = Arrays.stream(files).max((a, b) -> String.valueOf(a.getFilename()).compareTo(String.valueOf(b.getFilename()))).orElse(null);
            if (latest == null) {
                out.put("source", null);
                return out;
            }
            String md = latest.getContentAsString(StandardCharsets.UTF_8);
            int[] bySeverity = new int[5];
            int blockers = 0;
            List<Map<String, Object>> open = new ArrayList<>();
            for (String line : md.lines().toList()) {
                if (!line.startsWith("| ") || line.startsWith("| #") || line.startsWith("|---")) continue;
                String[] c = Arrays.stream(line.split("\\|")).map(String::strip).toArray(String[]::new);
                if (c.length < 11) continue;
                double mean;
                try {
                    mean = Double.parseDouble(c[8]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (!"aberto".equalsIgnoreCase(c[10])) continue;
                int sev = (int) Math.round(mean);
                bySeverity[sev]++;
                if (sev >= 3) blockers++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("screen", c[2]);
                item.put("heuristic", c[3]);
                item.put("severity", mean);
                item.put("decision", c[9]);
                open.add(item);
            }
            out.put("source", "docs/usabilidade/" + latest.getFilename());
            out.put("preliminary", md.contains("pré-avaliação"));
            out.put("openBySeverity", Map.of("0", bySeverity[0], "1", bySeverity[1], "2", bySeverity[2], "3", bySeverity[3], "4", bySeverity[4]));
            out.put("releaseBlockers", blockers);
            out.put("open", open);
        } catch (IOException e) {
            out.put("source", null);
            out.put("error", "planilha ilegível");
        }
        return out;
    }
}
