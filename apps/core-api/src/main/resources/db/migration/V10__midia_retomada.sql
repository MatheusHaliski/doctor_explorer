
-- V10 · Retomada do processamento de mídia (RF05). Se a API reinicia entre o "complete" e o fim da anonimização,
-- a tarefa em memória some; o job de limpeza reencontra QUARENTENA/PROCESSANDO parados pelo momento da última
-- transição e tenta de novo, até 3 tentativas (depois FALHOU, original apagado).

alter table media_asset add column state_changed_at timestamptz not null default now();
alter table media_asset add column processing_attempts integer not null default 0;

create index media_asset_stuck_idx on media_asset (state_changed_at) where state in ('QUARENTENA', 'PROCESSANDO');
