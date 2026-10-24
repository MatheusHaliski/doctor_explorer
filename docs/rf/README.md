# Catálogo de requisitos funcionais

Cada RF tem cabeçalho (`rf`, `titulo`, `status`, `cards`, `paths`), critérios de aceite, base legal e os
cinco diagramas em Mermaid (**máquina de estados, classes, sequência, componentes, atividades**), mais a
tabela de rastreabilidade. Mudar um arquivo listado em `paths` exige atualizar o doc **no mesmo commit**;
o job "Docs por RF" falha se não.

| RF | Título | Módulo | Épico | Fase | Status |
|---|---|---|---|---|---|
| [RF01](RF01-identidade-e-sessao.md) | Cadastro, login, 2FA e sessões | Médico | E1 | 2–3 | especificado |
| [RF02](RF02-verificacao-crm-rqe.md) | Verificação de CRM/RQE e selo | Médico | E1 | 4 | especificado |
| [RF03](RF03-perfil-profissional.md) | Perfil profissional verificado | Médico | E2 | 7 | especificado |
| [RF04](RF04-grupos-e-feed-de-casos.md) | Grupos por especialidade e feed de casos | Médico | E2 | 7 | especificado |
| [RF05](RF05-anonimizacao-de-midia.md) | Anonimização automática de mídia | Médico | E2 | 7 | especificado |
| [RF06](RF06-chat-e2ee.md) | Chat E2EE (MLS) | Médico | E3 | 8 | especificado |
| [RF07](RF07-video-e2ee.md) | Vídeo E2EE (WebRTC + SFrame) | Médico | E3 | 8 | especificado |
| [RF08](RF08-prontuario-soap.md) | Prontuário SOAP vinculado à discussão | Médico | E4 | 9 | especificado |
| [RF09](RF09-prescricao-e-exames-icp.md) | Prescrição e exames com ICP-Brasil | Médico | E4 | 10 | especificado |
| [RF10](RF10-lgpd-direitos-do-titular.md) | Consentimentos, painel do titular, exclusão | Transversal | E5 | 5 | especificado |
| [RF11](RF11-diretorio-hospitalar.md) | Diretório hospitalar e corpo clínico | Hospitalar | E6 | 7 | especificado |
| [RF12](RF12-rbac-hierarquico.md) | RBAC hierárquico por papel e selo | Hospitalar | E1 | 4 | especificado |
| [RF13](RF13-dashboard-administrativo.md) | Dashboard administrativo | Hospitalar | E6 | 11 | especificado |

## Épicos

| Épico | Nome | RFs |
|---|---|---|
| E1 | Identidade, verificação e acesso | RF01, RF02, RF12 |
| E2 | Rede profissional e discussão de casos | RF03, RF04, RF05 |
| E3 | Comunicação segura | RF06, RF07 |
| E4 | Ato clínico formal | RF08, RF09 |
| E5 | Privacidade e direitos do titular | RF10 |
| E6 | Gestão hospitalar | RF11, RF13 |

## Legenda da rastreabilidade

✅ implementado e testado · 🟡 em andamento · ⏳ pendente
