# Backlog · espelho do quadro Trello

Quadro: **Doctor Explorer · Modelagem Ágil** (Área de trabalho 2 de Matheus Haliski)
<https://trello.com/b/VcamNk6v/doctor-explorer-modelagem-%C3%A1gil>

Listas: 📘 Modelagem Ágil · 🧭 Épicos · 📋 Product Backlog · 🏃 Sprint 1 · Fundação · 🔄 Em andamento ·
👀 Em revisão / QA · ✅ Concluído · ❓ Questões abertas.

Este arquivo é a fonte da verdade do **conteúdo**; o Trello é a fonte da verdade do **estado** (coluna).
Quando um card muda de coluna, só o Trello muda; quando o texto muda, muda aqui e lá.

## 📘 Modelagem Ágil (já no quadro)

| Card | Resumo |
|---|---|
| 🩺 Identidade visual · Logo | logo oficial, paleta derivada, ação manual para capa/fundo |
| 🎯 Visão do produto | enunciado de visão + OKRs do MVP |
| 👥 Papéis e time (Scrum) | PO, SM, time, gates de conformidade, personas |
| 🔁 Cerimônias e cadência | sprint de 2 semanas, avaliação heurística antes de release |
| ✅ Definition of Ready | checklist |
| 🏁 Definition of Done | checklist |
| 📏 Estimativa, métricas e roadmap | Fibonacci, métricas, 11 sprints |

## 🧭 Épicos (no quadro desde 2026-10-24)

| Épico | Nome | RFs | Histórias |
|---|---|---|---|
| E1 | Identidade, verificação e acesso | RF01, RF02, RF12 | US01–US06, US25 |
| E2 | Rede profissional e discussão de casos | RF03, RF04, RF05 | US07–US11 |
| E3 | Comunicação segura (chat e vídeo E2EE) | RF06, RF07 | US12–US15 |
| E4 | Ato clínico formal (SOAP, receita, exame) | RF08, RF09 | US16–US21 |
| E5 | Privacidade e direitos do titular | RF10 | US22–US24 |
| E6 | Gestão hospitalar (diretório, dashboard) | RF11, RF13 | US26–US29 |
| E0 | Fundação técnica e Design System | — | T01–T08 |

## 📋 Product Backlog · histórias

Formato: **Como** _ator_ **quero** _ação_ **para** _valor_. Pontos em Fibonacci. Critérios em Gherkin
nos docs de RF.

| ID | História | RF | Pts |
|---|---|---|---|
| US01 | Como profissional quero me cadastrar com e-mail e aceitar os Termos (caixa desmarcada) para ter uma conta pendente | RF01 | 5 |
| US02 | Como profissional quero confirmar meu e-mail com código de 6 dígitos para ativar a conta | RF01 | 3 |
| US03 | Como profissional quero entrar com senha e TOTP para proteger minha conta | RF01 | 5 |
| US04 | Como profissional quero ver e encerrar minhas sessões em outros aparelhos | RF01 | 3 |
| US05 | Como médico quero enviar CRM/UF e RQE para receber o selo verificado | RF02 | 8 |
| US06 | Como moderador quero revisar manualmente selos sem resposta do CFM | RF02 | 3 |
| US07 | Como médico verificado quero editar meu perfil respeitando a publicidade médica | RF03 | 5 |
| US08 | Como médico verificado quero entrar no grupo da minha especialidade | RF04 | 3 |
| US09 | Como médico verificado quero publicar um caso sem dado identificável | RF04 | 8 |
| US10 | Como médico verificado quero dar parecer em um caso | RF04 | 3 |
| US11 | Como autor quero revisar a anonimização da mídia antes de publicar | RF05 | 8 |
| US12 | Como médico verificado quero conversar E2EE com um colega | RF06 | 13 |
| US13 | Como usuário quero denunciar uma mensagem encaminhando-a ao moderador | RF06 | 3 |
| US14 | Como médico verificado quero fazer uma chamada de vídeo E2EE | RF07 | 13 |
| US15 | Como participante quero compartilhar tela com aviso de dado de paciente | RF07 | 5 |
| US16 | Como médico verificado quero registrar nota SOAP vinculada a um caso | RF08 | 8 |
| US17 | Como médico quero retificar uma nota gerando nova versão com motivo | RF08 | 3 |
| US18 | Como integrador quero ler Composition/Encounter via FHIR R4 | RF08 | 5 |
| US19 | Como médico quero emitir receita assinada com ICP-Brasil | RF09 | 13 |
| US20 | Como médico quero solicitar exames por LOINC assinados com ICP-Brasil | RF09 | 8 |
| US21 | Como farmácia quero validar a receita pelo QR code | RF09 | 3 |
| US22 | Como titular quero ligar/desligar consentimentos por finalidade | RF10 | 3 |
| US23 | Como titular quero ver, corrigir e exportar meus dados | RF10 | 8 |
| US24 | Como titular quero excluir minha conta com carência e poder reativar | RF10 | 8 |
| US25 | Como gestor quero atribuir papéis na minha árvore de instituições | RF12 | 8 |
| US26 | Como gestor quero cadastrar minha instituição com CNPJ e CNES conferidos | RF11 | 5 |
| US27 | Como médico quero buscar instituições por especialidade e raio | RF11 | 5 |
| US28 | Como gestor quero ver métricas agregadas de engajamento | RF13 | 5 |
| US29 | Como auditor quero consultar e exportar a auditoria com justificativa | RF13 | 5 |
| US30 | Como profissional quero escolher a arte de fundo, a intensidade, o tema e o tamanho do texto, e ter isso em qualquer aparelho | RF01 | 3 |

## 🏃 Sprint 1 · Fundação · tarefas técnicas

| ID | Tarefa | Pts | Estado |
|---|---|---|---|
| T01 | apps/core-api (Spring Boot 4.1, Java 21) + Compose (PostGIS, Valkey, Garage, Cassandra `full`); workspace pnpm na raiz (`apps/web`, `packages/*`) | 5 | ✅ |
| T02 | Flyway V1 (contas, termos, consentimentos, preferências, user_key) + `SchemaConsistencyTest` | 5 | ✅ |
| T03 | `SecurityFilterChain`: CSRF SPA, CSP, Referrer-Policy, sessão Valkey, filtro de 24 h | 5 | ✅ |
| T04 | Problem Details em pt-BR + `422` com `errors[]` | 3 | ✅ |
| T05 | CI: `mvn verify` em JDK 21 e 25, Docs por RF (Mermaid), CodeQL, Gitleaks, Trivy, SBOM | 5 | ✅ |
| T06 | `packages/tokens`: DTCG → Style Dictionary → `tokens.css`/`tokens.ts` + `scripts/wcag_contrast.py` | 5 | 🟡 (tokens.css + contraste ✅; Style Dictionary ⏳) |
| T07 | Ícones SVGR em `packages/ui-web` a partir de `design/icons` | 3 | 🟡 (SVGs ✅; SVGR ⏳) |
| T08 | Protótipo `design/prototype/index.html` + `render.mjs` | 5 | ✅ |
| T09 | `apps/web`: Next.js 16 consumindo a core-api (login, cadastro, feed, caso, grupos, perfil, privacidade, segurança, instituições, gestão institucional, moderação) + jobs `web` e `web-e2e` no CI | 13 | ✅ |

Histórias já implementadas na API **e na web real (`apps/web`)**: **US30** (Aparência com 5 artes de fundo), **US01–US06** (cadastro, código, login em
etapas com TOTP, sessões, selo CRM/RQE; revisão manual US06 na fila `/moderacao`), **US08–US10** (grupos por RQE, caso
anonimizado com bloqueio por identificador, parecer; feed segmentado por tipo/especialidade/origem/estado),
**US07** (perfil com visibilidade e publicidade médica), **US22–US24** (consentimentos, seus
dados/exportação, exclusão com carência e reativação), **US25** (papéis globais + 2FA obrigatório; papéis
institucionais na árvore), **US26–US27** (instituição com CNPJ+CNES, busca por texto/UF/raio). Cobertura:
`AuthFlowIT`, `MfaIT`, `CredentialingIT`, `AccessMatrixIT`, `PrivacyIT`, `CommunityIT`, `DirectoryIT`.

## ❓ Questões abertas

Espelho de [QUESTOES_ABERTAS.md](QUESTOES_ABERTAS.md): um card por pergunta (Q01–Q18), com o dono na
descrição. **Criados no quadro em 2026-10-24**, junto com E0–E6 (checklist de histórias por épico),
US01–US29 (implementadas em ✅ Concluído, restantes em 📋 Product Backlog) e T01–T09 (T06, T07 e T09 em 🔄 Em andamento).
