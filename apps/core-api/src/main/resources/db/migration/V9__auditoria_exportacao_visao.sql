
-- V9 · A exportação guarda com que visão foi gerada (ex.: "platform:dpo", "institution:{id}:AUDITOR"). No download a
-- visão é recalculada pela sessão atual e precisa ser a mesma: quem perdeu o papel, o vínculo ou o 2FA não recebe
-- novas URLs (RF13). Exportações anteriores, sem visão, deixam de ser baixáveis (valiam 24 h).

alter table audit_export add column viewer_label text;
