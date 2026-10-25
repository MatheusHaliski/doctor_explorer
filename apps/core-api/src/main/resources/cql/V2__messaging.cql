-- RF06 · Delivery Service do chat MLS. Só bytes cifrados (mensagens de aplicação e commits MLS); o servidor não
-- tem as chaves. Cada linha expira com o TTL da retenção da conversa (1, 7 ou 90 dias), gravado no INSERT.

-- Mensagens e commits de uma conversa, na ordem em que o servidor os aceitou (seq vem do PostgreSQL).
CREATE TABLE IF NOT EXISTS {keyspace}.mls_message (
    conversation_id uuid,
    seq             bigint,
    id              uuid,
    kind            text,
    epoch           bigint,
    sender_device   uuid,
    sender_user     uuid,
    payload         blob,
    created_at      timestamp,
    PRIMARY KEY ((conversation_id), seq)
) WITH CLUSTERING ORDER BY (seq ASC);

-- Welcome de cada aparelho adicionado, até ele buscar e confirmar (TTL de 30 dias).
CREATE TABLE IF NOT EXISTS {keyspace}.mls_welcome (
    device_id       uuid,
    id              timeuuid,
    conversation_id uuid,
    epoch           bigint,
    payload         blob,
    PRIMARY KEY ((device_id), id)
) WITH CLUSTERING ORDER BY (id ASC);
