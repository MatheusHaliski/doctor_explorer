
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
