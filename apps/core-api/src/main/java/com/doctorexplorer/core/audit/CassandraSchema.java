package com.doctorexplorer.core.audit;

import com.datastax.oss.driver.api.core.CqlSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Cria keyspace e tabelas a partir de resources/cql/V*.cql, em ordem: auditoria (RF13) e Delivery Service do chat
 * (RF06). Idempotente; roda antes do primeiro uso.
 */
@Component
public class CassandraSchema {

    private final CqlSession session;
    private final AuditProperties props;
    private volatile boolean ready;

    public CassandraSchema(CqlSession session, AuditProperties props) {
        this.session = session;
        this.props = props;
    }

    static final List<String> FILES = List.of("cql/V1__audit.cql", "cql/V2__messaging.cql");

    public synchronized void ensure() {
        if (ready) return;
        for (String file : FILES) {
            String cql;
            try {
                cql = new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            String body = cql.lines().filter(l -> !l.stripLeading().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
            for (String stmt : Arrays.stream(body.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList()) {
                String resolved = stmt.replace("{keyspace}", props.keyspace()).replace("{replication}", props.replication());
                session.execute(com.datastax.oss.driver.api.core.cql.SimpleStatement.newInstance(resolved).setTimeout(Duration.ofSeconds(30)));
            }
        }
        ready = true;
    }

    public String keyspace() {
        return props.keyspace();
    }
}
