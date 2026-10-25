
-- V8 · Mídia de casos (RF05). O arquivo vai direto ao S3 de quarentena por URL pré-assinada; a anonimização roda
-- em memória e gera uma prévia privada; só a versão aprovada pelo autor vai para o bucket publicado. O original é
-- apagado na aprovação, no descarte ou em até 24 h pelo job de limpeza.
-- Estados: UPLOAD_PENDENTE → QUARENTENA → PROCESSANDO → AGUARDANDO_REVISAO → APROVADO | DESCARTADO,
--          e os finais EXPIRADO, REJEITADO, FALHOU.

create table media_asset (
    id                  uuid        primary key,
    owner_id            uuid        not null references user_account (id),
    kind                text        not null,
    state               text        not null,
    declared_type       text        not null,
    declared_size       bigint      not null,
    dermatology         boolean     not null default false,
    blur_faces          boolean,
    quarantine_key      text        not null,
    preview_key         text,
    published_key       text,
    output_type         text,
    width               integer,
    height              integer,
    preview_version     integer     not null default 0,
    report              text,
    reason              text,
    post_id             uuid        references case_post (id) on delete set null,
    created_at          timestamptz not null default now(),
    upload_expires_at   timestamptz not null,
    processed_at        timestamptz,
    approved_at         timestamptz,
    original_deleted_at timestamptz,
    constraint media_asset_kind_chk check (kind in ('IMAGE', 'DICOM')),
    constraint media_asset_state_chk check (state in ('UPLOAD_PENDENTE', 'QUARENTENA', 'PROCESSANDO', 'AGUARDANDO_REVISAO',
        'APROVADO', 'DESCARTADO', 'EXPIRADO', 'REJEITADO', 'FALHOU'))
);

create index media_asset_owner_idx on media_asset (owner_id, created_at desc);
create index media_asset_post_idx on media_asset (post_id) where post_id is not null;
create index media_asset_cleanup_idx on media_asset (created_at) where original_deleted_at is null;
