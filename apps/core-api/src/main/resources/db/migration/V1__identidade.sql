
-- V1 · Identidade: contas, credenciais, 2FA, verificação de e-mail, termos, consentimentos, preferências e chave por usuário.
-- IDs são UUID v7 gerados pela aplicação. Datas em timestamptz (UTC). Nada de dado de saúde neste schema.

create table user_account (
    id               uuid primary key,
    email            text        not null,
    email_normalized text        not null,
    display_name     text        not null,
    birth_date       date        not null,
    status           text        not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    activated_at     timestamptz,
    deletion_requested_at timestamptz,
    grace_ends_at    timestamptz,
    deleted_at       timestamptz,
    constraint user_account_status_chk check (status in (
        'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'PENDING_DELETION', 'ANONYMOUS_LGPD_DELETED')),
    constraint user_account_email_normalized_key unique (email_normalized)
);
comment on table user_account is 'Conta de acesso. Dados sociais; nunca dado clínico.';
comment on column user_account.email_normalized is 'E-mail em minúsculas, sem espaços, para unicidade e login.';
comment on column user_account.grace_ends_at is 'Fim da carência de 30 dias após o pedido de exclusão (LGPD Art. 18, VI).';

create table credential (
    user_id       uuid primary key references user_account (id) on delete cascade,
    password_hash text        not null,
    changed_at    timestamptz not null default now()
);
comment on column credential.password_hash is 'Argon2id via DelegatingPasswordEncoder ({argon2}...).';

create table mfa_secret (
    user_id            uuid primary key references user_account (id) on delete cascade,
    totp_secret_cipher bytea       not null,
    enabled            boolean     not null default false,
    confirmed_at       timestamptz,
    created_at         timestamptz not null default now()
);
comment on column mfa_secret.totp_secret_cipher is 'Segredo TOTP cifrado com AES-256-GCM pela subchave "mfa" derivada de DE_APP_SECRET.';

create table recovery_code (
    id        uuid primary key,
    user_id   uuid        not null references user_account (id) on delete cascade,
    code_hash text        not null,
    used_at   timestamptz,
    created_at timestamptz not null default now()
);
create index recovery_code_user_idx on recovery_code (user_id) where used_at is null;

create table email_verification (
    user_id    uuid primary key references user_account (id) on delete cascade,
    code_hash  text        not null,
    attempts   smallint    not null default 0,
    sent_at    timestamptz not null default now(),
    expires_at timestamptz not null,
    constraint email_verification_attempts_chk check (attempts between 0 and 5)
);

create table terms_acceptance (
    id              uuid primary key,
    user_id         uuid        not null references user_account (id) on delete cascade,
    terms_version   text        not null,
    privacy_version text        not null,
    accepted_at     timestamptz not null default now(),
    ip_hash         text        not null,
    user_agent      text
);
create index terms_acceptance_user_idx on terms_acceptance (user_id, accepted_at desc);
comment on table terms_acceptance is 'Aceite com versão, data e IP (hash) · LGPD Art. 7º, V e Art. 8º.';

create table consent_event (
    id           uuid primary key,
    user_id      uuid        not null references user_account (id) on delete cascade,
    purpose      text        not null,
    granted      boolean     not null,
    text_version text        not null,
    occurred_at  timestamptz not null default now(),
    ip_hash      text        not null,
    constraint consent_event_purpose_chk check (purpose in (
        'LOCATION', 'REMINDERS', 'CONTENT_RECOMMENDATION', 'INSTITUTIONAL_COMMUNICATION'))
);
create index consent_event_user_purpose_idx on consent_event (user_id, purpose, occurred_at desc);
comment on table consent_event is 'Histórico de consentimento por finalidade; o estado atual é o evento mais recente. Tudo desligado por padrão.';

create table user_preference (
    user_id          uuid primary key references user_account (id) on delete cascade,
    locale           text    not null default 'pt-BR',
    theme            text    not null default 'system',
    font_scale       smallint not null default 100,
    on_call_mode     boolean not null default false,
    updated_at       timestamptz not null default now(),
    constraint user_preference_theme_chk check (theme in ('system', 'light', 'dark')),
    constraint user_preference_font_scale_chk check (font_scale between 85 and 150)
);

create table user_key (
    user_id      uuid primary key references user_account (id) on delete cascade,
    wrapped_dek  bytea       not null,
    kek_version  integer     not null default 1,
    created_at   timestamptz not null default now(),
    destroyed_at timestamptz
);
comment on table user_key is 'DEK social por usuário, envelopada pela KEK. destroyed_at marca o crypto-shredding da eliminação.';

create table login_audit (
    id          uuid primary key,
    user_id     uuid references user_account (id) on delete set null,
    occurred_at timestamptz not null default now(),
    outcome     text        not null,
    ip_hash     text        not null,
    user_agent  text,
    constraint login_audit_outcome_chk check (outcome in ('SUCCESS', 'BAD_CREDENTIALS', 'MFA_REQUIRED', 'MFA_FAILED', 'LOCKED', 'LOGOUT'))
);
create index login_audit_user_idx on login_audit (user_id, occurred_at desc);
comment on table login_audit is 'Eventos de autenticação sem conteúdo sensível; alimenta "alertas de login" e o limite de tentativas quando o Valkey não está disponível.';
