-- GERADO por scripts/gen_schema.py a partir de db/migration/V*.sql. Não edite à mão.

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

-- V2 · Credenciamento (RF02) e papéis globais (RF12): perfil profissional, especialidades com RQE,
-- evidência de cada consulta ao CFM e papéis de plataforma (moderador, admin, DPO, diretor-técnico).

create table professional_profile (
    user_id      uuid primary key references user_account (id) on delete cascade,
    crm_number   text,
    crm_uf       text,
    seal_status  text        not null default 'NONE',
    seal_changed_at timestamptz not null default now(),
    visibility   text        not null default 'VERIFIED_NETWORK',
    bio          text,
    updated_at   timestamptz not null default now(),
    constraint professional_profile_seal_chk check (seal_status in (
        'NONE', 'PENDING', 'MANUAL_REVIEW', 'VERIFIED', 'REJECTED', 'REVOKED')),
    constraint professional_profile_visibility_chk check (visibility in (
        'VERIFIED_NETWORK', 'MY_INSTITUTION', 'PUBLIC_MINIMAL')),
    constraint professional_profile_uf_chk check (crm_uf is null or crm_uf ~ '^[A-Z]{2}$')
);
create unique index professional_profile_crm_verified_key on professional_profile (crm_uf, crm_number) where seal_status = 'VERIFIED';
comment on index professional_profile_crm_verified_key is 'Um CRM só pode estar VERIFIED em uma conta; o serviço checa antes e devolve 422/MANUAL_REVIEW em vez de erro.';
comment on table professional_profile is 'Perfil profissional; o selo VERIFIED libera escrita social e clínica.';

create table specialty_claim (
    id             uuid primary key,
    user_id        uuid    not null references user_account (id) on delete cascade,
    specialty_code text    not null,
    rqe            text    not null,
    confirmed      boolean not null default false,
    constraint specialty_claim_user_code_key unique (user_id, specialty_code)
);
comment on table specialty_claim is 'Especialidade declarada com RQE; confirmed = conferida na fonte do CFM.';

create table credential_check (
    id            uuid primary key,
    user_id       uuid        not null references user_account (id) on delete cascade,
    source        text        not null,
    checked_at    timestamptz not null default now(),
    response_hash text,
    outcome       text        not null,
    reason_pt_br  text,
    constraint credential_check_outcome_chk check (outcome in ('VERIFIED', 'REJECTED', 'UNAVAILABLE', 'NOT_FOUND'))
);
create index credential_check_user_idx on credential_check (user_id, checked_at desc);
comment on table credential_check is 'Evidência de cada consulta: fonte, data, hash da resposta e resultado. Guardada para auditoria.';

create table user_role (
    user_id    uuid        not null references user_account (id) on delete cascade,
    role       text        not null,
    granted_at timestamptz not null default now(),
    granted_by uuid        references user_account (id) on delete set null,
    primary key (user_id, role),
    constraint user_role_role_chk check (role in ('MODERATOR', 'ADMIN', 'DPO', 'TECHNICAL_DIRECTOR'))
);
comment on table user_role is 'Papéis globais além de PROFESSIONAL (implícito). Exigem 2FA ativo para usar as rotas protegidas.';

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

-- V4 · Comunidade (RF04): grupos fechados por especialidade, vínculo ao grupo, publicações classificadas
-- em quatro eixos (tipo, especialidade, origem, estado) e comentários/pareceres. Nenhum dado de paciente:
-- o texto passa pelo detector de identificadores antes de publicar.

create table specialty_group (
    id             uuid primary key,
    specialty_code text        not null unique,
    name           text        not null,
    join_policy    text        not null default 'RQE_OR_APPROVAL',
    created_at     timestamptz not null default now(),
    constraint specialty_group_policy_chk check (join_policy in ('RQE_OR_APPROVAL', 'APPROVAL_ONLY'))
);

create table group_membership (
    group_id  uuid        not null references specialty_group (id) on delete cascade,
    user_id   uuid        not null references user_account (id) on delete cascade,
    role      text        not null default 'MEMBER',
    state     text        not null default 'ACTIVE',
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id),
    constraint group_membership_role_chk check (role in ('MEMBER', 'MODERATOR')),
    constraint group_membership_state_chk check (state in ('REQUESTED', 'ACTIVE', 'REMOVED'))
);
create index group_membership_user_idx on group_membership (user_id) where state = 'ACTIVE';

create table case_post (
    id            uuid primary key,
    group_id      uuid        not null references specialty_group (id),
    author_id     uuid        not null references user_account (id),
    kind          text        not null,
    audience      text        not null default 'GROUP',
    state         text        not null default 'PUBLISHED',
    title         text        not null,
    complaint     text,
    history       text,
    exam          text,
    workup        text,
    question      text,
    tags          text        not null default '',
    comment_count integer     not null default 0,
    opinion_count integer     not null default 0,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    resolved_at   timestamptz,
    hidden_reason text,
    constraint case_post_kind_chk check (kind in ('CASE', 'OPINION_REQUEST', 'PROTOCOL', 'NOTICE', 'EDUCATION')),
    constraint case_post_audience_chk check (audience in ('GROUP', 'NETWORK')),
    constraint case_post_state_chk check (state in ('DRAFT', 'PUBLISHED', 'RESOLVED', 'HIDDEN', 'DELETED'))
);
create index case_post_feed_idx on case_post (created_at desc) where state in ('PUBLISHED', 'RESOLVED');
create index case_post_group_idx on case_post (group_id, created_at desc);
comment on table case_post is 'Publicação anonimizada. tags: lista separada por vírgula. Estado "sem resposta" = PUBLISHED com opinion_count = 0.';

create table post_comment (
    id         uuid primary key,
    post_id    uuid        not null references case_post (id) on delete cascade,
    author_id  uuid        not null references user_account (id),
    body       text        not null,
    is_opinion boolean     not null default false,
    created_at timestamptz not null default now(),
    hidden_reason text
);
create index post_comment_post_idx on post_comment (post_id, created_at);

-- grupos do MVP (mesmos códigos de CredentialingService.SPECIALTIES)
insert into specialty_group (id, specialty_code, name) values
    ('0199a000-0000-7000-8000-000000000001', 'CARDIO', 'Cardiologia'),
    ('0199a000-0000-7000-8000-000000000002', 'UTI',    'Medicina Intensiva'),
    ('0199a000-0000-7000-8000-000000000003', 'DERM',   'Dermatologia'),
    ('0199a000-0000-7000-8000-000000000004', 'PED',    'Pediatria'),
    ('0199a000-0000-7000-8000-000000000005', 'ORTO',   'Ortopedia e Traumatologia'),
    ('0199a000-0000-7000-8000-000000000006', 'NEURO',  'Neurologia'),
    ('0199a000-0000-7000-8000-000000000007', 'CLIN',   'Clínica Médica');

-- V5 · Diretório hospitalar (RF11), papéis institucionais (RF12) e perfil (RF03). Hierarquia rede → hospital →
-- unidade por parent_id. Localização em lat/lng com busca por raio em SQL (Haversine); PostGIS/GiST entra
-- quando o diretório crescer (ADR-013). Publicações institucionais ganham audiência INSTITUTION no feed.

create table institution (
    id          uuid primary key,
    legal_name  text        not null,
    trade_name  text        not null,
    cnpj        text        not null unique,
    cnes        text,
    type        text        not null,
    parent_id   uuid        references institution (id),
    city        text        not null,
    uf          text        not null,
    lat         double precision,
    lng         double precision,
    state       text        not null default 'VERIFICANDO',
    created_by  uuid        not null references user_account (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint institution_type_chk check (type in ('NETWORK', 'HOSPITAL', 'UNIT')),
    constraint institution_state_chk check (state in ('VERIFICANDO', 'VERIFICADA', 'REJEITADA', 'SUSPENSA')),
    constraint institution_uf_chk check (uf ~ '^[A-Z]{2}$'),
    constraint institution_cnpj_chk check (cnpj ~ '^\d{14}$')
);
create index institution_parent_idx on institution (parent_id);
create index institution_city_idx on institution (uf, city);
comment on table institution is 'Rede, hospital ou unidade (UTI, centro cirúrgico...). Só VERIFICADA aparece no diretório.';

create table institution_check (
    id            uuid primary key,
    institution_id uuid       not null references institution (id) on delete cascade,
    source        text        not null,
    checked_at    timestamptz not null default now(),
    response_hash text,
    outcome       text        not null,
    reason_pt_br  text,
    constraint institution_check_outcome_chk check (outcome in ('VERIFIED', 'REJECTED', 'UNAVAILABLE', 'NOT_FOUND'))
);
create index institution_check_inst_idx on institution_check (institution_id, checked_at desc);

create table institution_membership (
    institution_id uuid        not null references institution (id) on delete cascade,
    user_id        uuid        not null references user_account (id) on delete cascade,
    role           text        not null default 'STAFF_PHYSICIAN',
    state          text        not null,
    since          timestamptz not null default now(),
    primary key (institution_id, user_id),
    constraint institution_membership_role_chk check (role in (
        'NETWORK_ADMIN', 'HOSPITAL_ADMIN', 'CLINICAL_DIRECTOR', 'UNIT_MANAGER', 'STAFF_PHYSICIAN', 'AUDITOR')),
    constraint institution_membership_state_chk check (state in ('INVITED', 'REQUESTED', 'ACTIVE', 'ENDED'))
);
create index institution_membership_user_idx on institution_membership (user_id) where state = 'ACTIVE';
comment on table institution_membership is 'Vínculo aceito pelos dois lados: a instituição convida ou o médico pede, e o outro confirma. Permissão herda para baixo na árvore.';

alter table case_post drop constraint case_post_audience_chk;
alter table case_post add constraint case_post_audience_chk check (audience in ('GROUP', 'NETWORK', 'INSTITUTION'));
alter table case_post add column institution_id uuid references institution (id);
create index case_post_institution_idx on case_post (institution_id, created_at desc) where institution_id is not null;
