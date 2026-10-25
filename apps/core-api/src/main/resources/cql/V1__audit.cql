-- RF13 · auditoria append-only. Executado por CassandraSchema na subida (idempotente: IF NOT EXISTS).
-- {keyspace} e {replication} vêm de doctor-explorer.cassandra.* (SimpleStrategy em dev/it; NetworkTopologyStrategy em produção).

CREATE KEYSPACE IF NOT EXISTS {keyspace} WITH replication = {replication};

-- Um evento por linha, particionado por escopo e dia (consulta por período = algumas partições).
-- seq é a posição na cadeia do escopo; hash = SHA-256(prev_hash | campos canônicos | seq).
CREATE TABLE IF NOT EXISTS {keyspace}.audit_event (
    scope         text,
    day           date,
    seq           bigint,
    id            uuid,
    at            timestamp,
    actor_id      uuid,
    actor_roles   text,
    action        text,
    resource_type text,
    resource_id   text,
    patient_ref   text,
    outcome       text,
    justification text,
    ip_hash       text,
    details       map<text, text>,
    prev_hash     text,
    hash          text,
    PRIMARY KEY ((scope, day), seq, id)
) WITH CLUSTERING ORDER BY (seq ASC, id ASC);

-- Cabeça da cadeia de cada escopo, avançada por LWT (compare-and-set): seguro com várias instâncias.
CREATE TABLE IF NOT EXISTS {keyspace}.audit_chain_head (
    scope text PRIMARY KEY,
    seq   bigint,
    hash  text,
    day   date
);
