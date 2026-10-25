package com.doctorexplorer.core.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** doctor-explorer.cassandra.*: keyspace e replicação da auditoria. */
@ConfigurationProperties("doctor-explorer.cassandra")
public record AuditProperties(String keyspace, String replication) {

    public AuditProperties {
        if (keyspace == null || keyspace.isBlank()) keyspace = "de_audit";
        if (!keyspace.matches("[a-z_][a-z0-9_]{0,47}")) throw new IllegalArgumentException("keyspace inválido: " + keyspace);
        if (replication == null || replication.isBlank()) replication = "{'class': 'SimpleStrategy', 'replication_factor': 1}";
    }
}
