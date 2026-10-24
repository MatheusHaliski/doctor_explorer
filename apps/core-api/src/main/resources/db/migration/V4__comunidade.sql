
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
