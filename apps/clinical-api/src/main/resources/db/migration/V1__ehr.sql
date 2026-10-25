
-- V1 · Prontuário (RF08). Banco próprio da clinical-api (ADR-001): nada aqui é lido pela core-api.
-- Campos com dado de paciente ou conteúdo clínico são cifrados por cofre (AES-256-GCM, chave de dados do cofre
-- embrulhada pela chave mestra; em produção, KMS). Busca por nome usa índice cego (HMAC por cofre).
-- Nota assinada é imutável no próprio banco (gatilho abaixo); correção é nova versão com motivo.
-- Guarda mínima de 20 anos (Lei 13.787/2018): não há exclusão de nota nem de paciente pela API.

create table clinical_vault (
    id          uuid        primary key,
    owner_type  text        not null,
    owner_id    uuid        not null,
    wrapped_dek bytea       not null,
    key_id      text        not null,
    created_at  timestamptz not null default now(),
    constraint clinical_vault_owner_type_chk check (owner_type in ('PRACTITIONER', 'INSTITUTION')),
    constraint clinical_vault_owner_uk unique (owner_type, owner_id)
);

create table practitioner (
    id          uuid        primary key,
    name        text        not null,
    crm_number  text,
    crm_uf      text,
    updated_at  timestamptz not null default now()
);

create table patient (
    id                  uuid        primary key,
    vault_id            uuid        not null references clinical_vault (id),
    name_enc            bytea       not null,
    birth_date_enc      bytea       not null,
    cpf_enc             bytea,
    sex                 text,
    local_record_number text,
    created_by          uuid        not null references practitioner (id),
    created_at          timestamptz not null default now(),
    last_record_at      timestamptz not null default now(),
    constraint patient_sex_chk check (sex is null or sex in ('female', 'male', 'other', 'unknown'))
);

create index patient_vault_idx on patient (vault_id);

-- índice cego: HMAC(chave do cofre, prefixo normalizado de cada palavra do nome)
create table patient_name_index (
    patient_id uuid  not null references patient (id),
    token      text  not null,
    primary key (token, patient_id)
);

create table encounter (
    id              uuid        primary key,
    vault_id        uuid        not null references clinical_vault (id),
    patient_id      uuid        not null references patient (id),
    practitioner_id uuid        not null references practitioner (id),
    context         text        not null,
    occurred_at     timestamptz not null,
    created_at      timestamptz not null default now(),
    constraint encounter_context_chk check (context in ('CONSULTA', 'DISCUSSAO_DE_CASO', 'TELECONSULTA', 'INTERCONSULTA'))
);

create index encounter_patient_idx on encounter (patient_id, occurred_at desc);

create table soap_composition (
    id               uuid        primary key,
    vault_id         uuid        not null references clinical_vault (id),
    encounter_id     uuid        not null references encounter (id),
    patient_id       uuid        not null references patient (id),
    author_id        uuid        not null references practitioner (id),
    lineage_id       uuid        not null,
    version          integer     not null,
    state            text        not null,
    subjective_enc   bytea,
    objective_enc    bytea,
    assessment_enc   bytea,
    plan_enc         bytea,
    icd10            text        not null default '',
    snomed           text        not null default '',
    links            text        not null default '[]',
    amend_reason     text,
    previous_id      uuid        references soap_composition (id),
    superseded_by    uuid        references soap_composition (id),
    content_hash     text,
    signature        text,
    signing_key_id   text,
    signed_at        timestamptz,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint soap_state_chk check (state in ('RASCUNHO', 'ASSINADA', 'RETIFICADA', 'DESCARTADA')),
    constraint soap_lineage_version_uk unique (lineage_id, version),
    constraint soap_signed_complete_chk check (state in ('RASCUNHO', 'DESCARTADA') or (content_hash is not null and signature is not null and signed_at is not null)),
    constraint soap_amend_reason_chk check (version = 1 or char_length(coalesce(amend_reason, '')) >= 10)
);

create index soap_patient_idx on soap_composition (patient_id, created_at desc);
create index soap_encounter_idx on soap_composition (encounter_id);
-- uma só versão em rascunho por linhagem
create unique index soap_one_draft_per_lineage on soap_composition (lineage_id) where state = 'RASCUNHO';

-- Imutabilidade no banco: depois de assinada, só o estado ASSINADA → RETIFICADA e o ponteiro para a nova versão
-- podem mudar. Excluir nota assinada também é recusado.
create function soap_composition_guard() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' then
        if old.state in ('ASSINADA', 'RETIFICADA') then
            raise exception 'nota assinada é imutável e não pode ser excluída (%)', old.id using errcode = 'P0001';
        end if;
        return old;
    end if;
    if old.state in ('ASSINADA', 'RETIFICADA') then
        if (to_jsonb(new) - 'state' - 'superseded_by' - 'updated_at') is distinct from (to_jsonb(old) - 'state' - 'superseded_by' - 'updated_at')
           or (new.state is distinct from old.state and not (old.state = 'ASSINADA' and new.state = 'RETIFICADA'))
           or (old.superseded_by is not null and new.superseded_by is distinct from old.superseded_by) then
            raise exception 'nota assinada é imutável (%)', old.id using errcode = 'P0001';
        end if;
    end if;
    return new;
end
$$;

create trigger soap_composition_immutable before update or delete on soap_composition
    for each row execute function soap_composition_guard();
