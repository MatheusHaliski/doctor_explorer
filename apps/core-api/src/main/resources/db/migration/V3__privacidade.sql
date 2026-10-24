
-- V3 · Privacidade (RF10): pedidos de exportação de dados e canal do Encarregado. Consentimentos e carência
-- de exclusão já estão em V1 (consent_event, user_account.grace_ends_at).

create table data_export_request (
    id           uuid primary key,
    user_id      uuid        not null references user_account (id) on delete cascade,
    state        text        not null default 'READY',
    format_version text      not null default '1',
    payload      text,
    requested_at timestamptz not null default now(),
    ready_at     timestamptz,
    expires_at   timestamptz not null,
    downloaded_at timestamptz,
    constraint data_export_request_state_chk check (state in ('REQUESTED', 'GENERATING', 'READY', 'DOWNLOADED', 'EXPIRED', 'FAILED'))
);
create index data_export_request_user_idx on data_export_request (user_id, requested_at desc);
comment on table data_export_request is 'Art. 18, V: cópia dos dados em JSON versionado. O ZIP cifrado por URL pré-assinada entra com o módulo de mídia (S3).';

create table dpo_ticket (
    id           uuid primary key,
    protocol     text        not null unique,
    user_id      uuid        references user_account (id) on delete set null,
    type         text        not null,
    message      text        not null,
    state        text        not null default 'OPEN',
    opened_at    timestamptz not null default now(),
    due_at       timestamptz not null,
    answered_at  timestamptz,
    answer       text,
    constraint dpo_ticket_type_chk check (type in (
        'ACCESS', 'CORRECTION', 'ANONYMIZATION', 'PORTABILITY', 'DELETION', 'SHARING_INFO', 'CONSENT_INFO', 'REVOCATION', 'INCIDENT', 'OTHER')),
    constraint dpo_ticket_state_chk check (state in ('OPEN', 'ANSWERED', 'CLOSED'))
);
create index dpo_ticket_state_idx on dpo_ticket (state, due_at);
comment on table dpo_ticket is 'Art. 41: pedidos do titular ao Encarregado, com protocolo e prazo de resposta (15 dias, ⚖️ Q13).';
