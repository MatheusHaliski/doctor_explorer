
-- V11 · Chat E2EE com MLS (RF06). O servidor é o Delivery Service: guarda aparelhos e suas chaves públicas,
-- KeyPackages (públicos, de uso único), conversas e membros. Mensagens e commits cifrados ficam na Cassandra
-- com TTL; aqui não há texto de mensagem. A única exceção é a denúncia, em que quem denuncia encaminha, por
-- ação explícita, o trecho já decifrado ao moderador.

create table messaging_device (
    id              uuid primary key,
    user_id         uuid        not null references user_account (id) on delete cascade,
    name            text        not null,
    signature_key   bytea       not null,
    state           text        not null,
    approved_via    text,
    approved_by     uuid,
    created_at      timestamptz not null default now(),
    approved_at     timestamptz,
    revoked_at      timestamptz,
    last_seen_at    timestamptz,
    constraint messaging_device_state_chk check (state in ('PENDING', 'APPROVED', 'REVOKED')),
    constraint messaging_device_via_chk check (approved_via is null or approved_via in ('FIRST', 'DEVICE', 'RECOVERY')),
    constraint messaging_device_key_len check (octet_length(signature_key) = 32),
    constraint messaging_device_key_uq unique (signature_key)
);
create index messaging_device_user_idx on messaging_device (user_id, state);

-- Frase de recuperação: o navegador deriva um verificador (PBKDF2) e o servidor guarda só o Argon2id dele.
create table messaging_recovery (
    user_id         uuid primary key references user_account (id) on delete cascade,
    verifier_hash   text        not null,
    created_at      timestamptz not null default now()
);

create table messaging_key_package (
    id              uuid primary key,
    device_id       uuid        not null references messaging_device (id) on delete cascade,
    payload         bytea       not null,
    created_at      timestamptz not null default now(),
    consumed_at     timestamptz
);
create index messaging_key_package_free_idx on messaging_key_package (device_id, created_at) where consumed_at is null;

create table conversation (
    id              uuid primary key,
    kind            text        not null,
    title           text,
    created_by      uuid        not null references user_account (id),
    direct_key      text,
    epoch           bigint      not null default 0,
    next_seq        bigint      not null default 1,
    last_app_seq    bigint      not null default 0,
    retention_days  int         not null default 90,
    state           text        not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    last_message_at timestamptz,
    constraint conversation_kind_chk check (kind in ('DIRECT', 'GROUP')),
    constraint conversation_state_chk check (state in ('ACTIVE', 'ARCHIVED')),
    constraint conversation_retention_chk check (retention_days in (1, 7, 90)),
    constraint conversation_direct_uq unique (direct_key)
);

create table conversation_member (
    conversation_id uuid        not null references conversation (id) on delete cascade,
    user_id         uuid        not null references user_account (id) on delete cascade,
    role            text        not null,
    joined_at       timestamptz not null default now(),
    left_at         timestamptz,
    last_read_seq   bigint      not null default 0,
    primary key (conversation_id, user_id),
    constraint conversation_member_role_chk check (role in ('OWNER', 'MEMBER'))
);
create index conversation_member_user_idx on conversation_member (user_id) where left_at is null;

-- Quais aparelhos estão na árvore MLS de cada conversa, segundo os commits aceitos (o cliente confere de novo).
create table conversation_device (
    conversation_id uuid        not null references conversation (id) on delete cascade,
    device_id       uuid        not null references messaging_device (id) on delete cascade,
    added_epoch     bigint      not null,
    removed_epoch   bigint,
    primary key (conversation_id, device_id)
);

create table message_report (
    id              uuid primary key,
    conversation_id uuid        not null references conversation (id) on delete cascade,
    reporter_id     uuid        not null references user_account (id),
    reported_user_id uuid       references user_account (id),
    reason          text        not null,
    excerpt         text        not null,
    state           text        not null default 'OPEN',
    created_at      timestamptz not null default now(),
    resolved_by     uuid,
    resolved_at     timestamptz,
    resolution      text,
    constraint message_report_state_chk check (state in ('OPEN', 'RESOLVED'))
);
create index message_report_open_idx on message_report (created_at) where state = 'OPEN';

create table conversation_attachment (
    id              uuid primary key,
    conversation_id uuid        not null references conversation (id) on delete cascade,
    uploader_id     uuid        not null references user_account (id),
    size            bigint      not null,
    created_at      timestamptz not null default now(),
    expires_at      timestamptz not null
);
create index conversation_attachment_exp_idx on conversation_attachment (expires_at);
