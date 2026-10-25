
-- V2 · Receita e pedido de exame (RF09). O conteúdo (itens, indicação) e o PDF assinado ficam cifrados com a chave do
-- cofre; o código de validação é buscado pelo hash (a cópia cifrada serve só para o médico revê-lo). Documento assinado não se apaga: cancela com motivo (gatilho
-- abaixo). Estados: RASCUNHO → ASSINANDO → ASSINADA → ENVIADA, FALHA_ASSINATURA (volta a assinar), CANCELADA.

create table clinical_document (
    id                  uuid        primary key,
    vault_id            uuid        not null references clinical_vault (id),
    patient_id          uuid        not null references patient (id),
    encounter_id        uuid        not null references encounter (id),
    author_id           uuid        not null references practitioner (id),
    kind                text        not null,
    state               text        not null,
    content_enc         bytea,
    content_hash        text,
    validation_code_hash text       unique,
    validation_code_enc bytea,
    pdf_enc             bytea,
    pdf_sha256          text,
    signature_level     text,
    signature_provider  text,
    cert_subject        text,
    cert_serial         text,
    signed_at           timestamptz,
    valid_until         date,
    failure_reason      text,
    cancel_reason       text,
    canceled_at         timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint clinical_document_kind_chk check (kind in ('RECEITA', 'EXAME')),
    constraint clinical_document_state_chk check (state in ('RASCUNHO', 'ASSINANDO', 'FALHA_ASSINATURA', 'ASSINADA', 'ENVIADA', 'CANCELADA')),
    constraint clinical_document_signed_chk check (state in ('RASCUNHO', 'ASSINANDO', 'FALHA_ASSINATURA')
        or (pdf_enc is not null and pdf_sha256 is not null and signed_at is not null and validation_code_hash is not null)),
    constraint clinical_document_cancel_chk check (state <> 'CANCELADA' or char_length(coalesce(cancel_reason, '')) >= 10)
);

create index clinical_document_patient_idx on clinical_document (patient_id, created_at desc);

-- Depois de assinado, só mudam estado (ENVIADA/CANCELADA), motivo e data do cancelamento; nada se apaga.
create function clinical_document_guard() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' then
        if old.state in ('ASSINADA', 'ENVIADA', 'CANCELADA') then
            raise exception 'documento assinado é imutável e não pode ser excluído (%)', old.id using errcode = 'P0001';
        end if;
        return old;
    end if;
    if old.state in ('ASSINADA', 'ENVIADA', 'CANCELADA') then
        if (to_jsonb(new) - 'state' - 'cancel_reason' - 'canceled_at' - 'updated_at') is distinct from (to_jsonb(old) - 'state' - 'cancel_reason' - 'canceled_at' - 'updated_at')
           or old.state = 'CANCELADA'
           or new.state not in ('ASSINADA', 'ENVIADA', 'CANCELADA') then
            raise exception 'documento assinado é imutável (%)', old.id using errcode = 'P0001';
        end if;
    end if;
    return new;
end
$$;

create trigger clinical_document_immutable before update or delete on clinical_document
    for each row execute function clinical_document_guard();

-- Entrega ao paciente: link de uso único, 7 dias. Só o hash do token fica guardado; o destino aparece mascarado.
create table document_delivery (
    id                 uuid        primary key,
    document_id        uuid        not null references clinical_document (id),
    channel            text        not null,
    destination_masked text,
    token_hash         text        not null unique,
    expires_at         timestamptz not null,
    used_at            timestamptz,
    created_at         timestamptz not null default now(),
    constraint document_delivery_channel_chk check (channel in ('EMAIL', 'SMS', 'LINK'))
);

-- Modelos do médico (heurística 7): só itens e textos de posologia, nunca dado de paciente.
create table document_template (
    id         uuid        primary key,
    author_id  uuid        not null references practitioner (id),
    kind       text        not null,
    name       text        not null,
    items      text        not null,
    created_at timestamptz not null default now(),
    constraint document_template_kind_chk check (kind in ('RECEITA', 'EXAME')),
    constraint document_template_name_uk unique (author_id, kind, name)
);
