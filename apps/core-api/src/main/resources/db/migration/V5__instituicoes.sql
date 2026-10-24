
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
