
-- V12 · Chamadas de vídeo E2EE (RF07). Só metadados: quem estava, quando, quanto durou e a qualidade da rede.
-- Mídia passa pelo SFU (apps/rtc) cifrada quadro a quadro com a chave do grupo MLS da conversa; nada é gravado.

create table call_session (
    id               uuid primary key,
    conversation_id  uuid        not null references conversation (id) on delete cascade,
    started_by       uuid        not null references user_account (id),
    state            text        not null default 'RINGING',
    created_at       timestamptz not null default now(),
    started_at       timestamptz,
    ended_at         timestamptz,
    max_participants int         not null default 1,
    quality          text,
    screen_shared    boolean     not null default false,
    constraint call_session_state_chk check (state in ('RINGING', 'ACTIVE', 'ENDED', 'MISSED', 'DECLINED')),
    constraint call_session_quality_chk check (quality is null or quality in ('BOA', 'REGULAR', 'RUIM'))
);
create index call_session_open_idx on call_session (conversation_id) where state in ('RINGING', 'ACTIVE');

create table call_participant (
    call_id    uuid        not null references call_session (id) on delete cascade,
    user_id    uuid        not null references user_account (id) on delete cascade,
    device_id  uuid        not null references messaging_device (id) on delete cascade,
    joined_at  timestamptz not null default now(),
    left_at    timestamptz,
    quality    text,
    primary key (call_id, user_id),
    constraint call_participant_quality_chk check (quality is null or quality in ('BOA', 'REGULAR', 'RUIM'))
);
