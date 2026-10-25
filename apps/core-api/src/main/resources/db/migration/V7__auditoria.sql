
-- V7 · Exportações da auditoria (RF13). Os eventos ficam na Cassandra (append-only, hash encadeado); aqui só o
-- pedido de exportação e seu estado: SOLICITADA → GERANDO → PRONTA → BAIXADA | EXPIRADA (24 h) | FALHOU.
-- O CSV e a assinatura Ed25519 ficam no bucket de exportações (Object Lock em produção).

create table audit_export (
    id             uuid        primary key,
    requested_by   uuid        not null references user_account (id),
    scope          text        not null,
    filters        text        not null,
    justification  text        not null,
    reveal_patient boolean     not null default false,
    state          text        not null,
    object_key     text,
    sha256         text,
    signature      text,
    event_count    integer,
    created_at     timestamptz not null default now(),
    ready_at       timestamptz,
    expires_at     timestamptz,
    downloaded_at  timestamptz,
    constraint audit_export_state_chk check (state in ('SOLICITADA', 'GERANDO', 'PRONTA', 'BAIXADA', 'EXPIRADA', 'FALHOU')),
    constraint audit_export_justification_chk check (char_length(justification) >= 10)
);

create index audit_export_requested_by_idx on audit_export (requested_by, created_at desc);
