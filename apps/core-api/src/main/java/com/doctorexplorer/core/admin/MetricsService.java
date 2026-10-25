package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.audit.AuditEvent;
import com.doctorexplorer.core.audit.CassandraAuditStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * RF13 · engajamento agregado. Escopo = plataforma (sem filtro) ou corpo vinculado a uma árvore de instituições.
 * Nada de dado de saúde: contagens de publicações, pareceres e tempos. Toda célula passa pelo {@link KAnonymityGuard}.
 */
@Service
public class MetricsService {

    public static final int MAX_DAYS = 90;

    private final NamedParameterJdbcTemplate jdbc;
    private final CassandraAuditStore audit;

    public MetricsService(NamedParameterJdbcTemplate jdbc, CassandraAuditStore audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** members nulo = plataforma inteira; senão, só quem tem vínculo ativo na árvore. */
    public Map<String, Object> engagement(Set<UUID> members, int days, String auditScope) {
        int d = Math.max(7, Math.min(days, MAX_DAYS));
        Instant to = Instant.now();
        Instant from = to.minus(d, ChronoUnit.DAYS);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("from", Timestamp.from(from)).addValue("to", Timestamp.from(to))
                .addValue("members", members == null ? List.of(new UUID(0, 0)) : List.copyOf(members.isEmpty() ? Set.of(new UUID(0, 0)) : members))
                .addValue("all", members == null);

        long active = one("select count(distinct user_id) from login_audit where outcome = 'SUCCESS' and occurred_at between :from and :to"
                + " and (:all or user_id in (:members))", p);
        long[] cases = two("select count(*), count(distinct author_id) from case_post where kind = 'CASE' and state in ('PUBLISHED', 'RESOLVED')"
                + " and created_at between :from and :to and (:all or author_id in (:members))", p);
        long[] opinions = two("select count(*), count(distinct c.author_id) from post_comment c where c.is_opinion and c.hidden_reason is null"
                + " and c.created_at between :from and :to and (:all or c.author_id in (:members))", p);
        Map<String, Object> median = jdbc.queryForMap("""
                select percentile_cont(0.5) within group (order by extract(epoch from first_op - created_at)) as median_s,
                       count(distinct author_id) as people
                from (select p.id, p.author_id, p.created_at,
                             (select min(c.created_at) from post_comment c where c.post_id = p.id and c.is_opinion) as first_op
                      from case_post p
                      where p.kind = 'CASE' and p.state in ('PUBLISHED', 'RESOLVED') and p.created_at between :from and :to
                        and (:all or p.author_id in (:members))) t
                where first_op is not null""", p);

        // chamadas (RF07) vêm da auditoria: eventos call.start no escopo
        long calls = 0;
        Set<UUID> callers = new HashSet<>();
        for (AuditEvent e : audit.range(auditScope, LocalDate.ofInstant(from, ZoneOffset.UTC), LocalDate.ofInstant(to, ZoneOffset.UTC))) {
            if (e.action().equals("call.start") && (members == null || members.contains(e.actorId()))) {
                calls++;
                if (e.actorId() != null) callers.add(e.actorId());
            }
        }
        double weeks = d / 7.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodDays", d);
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("minCell", KAnonymityGuard.MIN_CELL);
        out.put("activeUsers", KAnonymityGuard.cell(active, active));
        out.put("casesPublished", KAnonymityGuard.cell(cases[0], cases[1]));
        out.put("opinions", KAnonymityGuard.cell(opinions[0], opinions[1]));
        Number medianS = (Number) median.get("median_s");
        out.put("medianHoursToFirstOpinion", KAnonymityGuard.cell(medianS == null ? null : Math.round(medianS.doubleValue() / 36.0) / 100.0,
                ((Number) median.get("people")).longValue()));
        out.put("callsPerWeek", KAnonymityGuard.cell(Math.round(calls / weeks * 10) / 10.0, callers.size()));
        out.put("weekly", weekly(p, members));
        return out;
    }

    /** Últimas 8 semanas ISO: casos e pareceres por semana, cada ponto com seu próprio k-anonimato. */
    private List<Map<String, Object>> weekly(MapSqlParameterSource base, Set<UUID> members) {
        LocalDate monday = LocalDate.now(ZoneOffset.UTC).with(java.time.DayOfWeek.MONDAY);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate start = monday.minusWeeks(i);
            MapSqlParameterSource p = new MapSqlParameterSource(base.getValues())
                    .addValue("from", Timestamp.from(start.atStartOfDay().toInstant(ZoneOffset.UTC)))
                    .addValue("to", Timestamp.from(start.plusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            long[] c = two("select count(*), count(distinct author_id) from case_post where kind = 'CASE' and state in ('PUBLISHED', 'RESOLVED')"
                    + " and created_at >= :from and created_at < :to and (:all or author_id in (:members))", p);
            long[] o = two("select count(*), count(distinct author_id) from post_comment where is_opinion and hidden_reason is null"
                    + " and created_at >= :from and created_at < :to and (:all or author_id in (:members))", p);
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("week", start.get(IsoFields.WEEK_BASED_YEAR) + "-S" + String.format("%02d", start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
            w.put("weekStart", start.toString());
            w.put("cases", KAnonymityGuard.cell(c[0], c[1]));
            w.put("opinions", KAnonymityGuard.cell(o[0], o[1]));
            out.add(w);
        }
        return out;
    }

    private long one(String sql, MapSqlParameterSource p) {
        Long v = jdbc.queryForObject(sql, p, Long.class);
        return v == null ? 0 : v;
    }

    private long[] two(String sql, MapSqlParameterSource p) {
        return jdbc.queryForObject(sql, p, (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }
}
