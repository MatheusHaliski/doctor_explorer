<p align="center"><img src="design/brand/logo-doctor-explorer.svg" width="320" alt="Doctor Explorer"></p>

# Doctor Explorer

Rede social profissional **fechada e verificada** para médicos, profissionais de saúde e redes
hospitalares. Casos clínicos anonimizados, chat e vídeo com criptografia ponta a ponta, nota SOAP no
prontuário, receita e pedido de exame com ICP-Brasil, diretório hospitalar com RBAC. Conformidade com
**CFM, LGPD e Anvisa** desde o primeiro dia.

Estado atual: **MVP em construção**. `apps/core-api` cobre RF01–RF05 e RF10–RF13 (identidade, selo
CRM/RQE, perfil, grupos e feed, anonimização de mídia, LGPD, diretório hospitalar, RBAC, auditoria e painéis);
`apps/clinical-api` cobre o RF08 (prontuário SOAP no cofre clínico e fachada FHIR R4) e o RF09 (receita e pedido de
exame em PDF/A com assinatura PAdES, QR e validador público); `apps/web` é a interface
real em Next.js sobre as duas. Ordem de entrega em
[docs/ESPECIFICACAO_TECNICA.md §11](docs/ESPECIFICACAO_TECNICA.md#11-plano-de-entrega); estado por
história em [docs/TRELLO_BACKLOG.md](docs/TRELLO_BACKLOG.md).

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/ESPECIFICACAO_TECNICA.md](docs/ESPECIFICACAO_TECNICA.md) | arquitetura, compliance, qualidade, usabilidade, MVP, dados, API, infra |
| [docs/rf/README.md](docs/rf/README.md) | 13 requisitos funcionais, cada um com estados, classes, sequência, componentes e atividades |
| [docs/CONFORMIDADE.md](docs/CONFORMIDADE.md) | matriz requisito → norma → controle → evidência |
| [docs/PESQUISA_TECNOLOGIAS.md](docs/PESQUISA_TECNOLOGIAS.md) | ADRs |
| [docs/QUESTOES_ABERTAS.md](docs/QUESTOES_ABERTAS.md) | decisões que dependem do negócio |
| [docs/TAXONOMIA_ATORES_E_CARDS.md](docs/TAXONOMIA_ATORES_E_CARDS.md) | atores e anatomia dos cards |
| [docs/TRELLO_BACKLOG.md](docs/TRELLO_BACKLOG.md) | espelho do quadro Trello |
| `docs/DIAGRAMA_*.md`, [docs/MAQUINA_ESTADOS.md](docs/MAQUINA_ESTADOS.md) | diagramas transversais |
| `docs/pesquisa/` | pesquisas de apoio (tipos de usuário, funcionalidades, benchmark) |

## Design

- Logo oficial: `design/brand/logo-doctor-explorer.svg`; marca compacta: `design/brand/logo-mark.svg`.
- Ícones da barra lateral e da topbar, em plástico azul derivado do logo: `design/icons/` (gerados por
  `design/icons/build_icons.py`; prévia em `design/icons/preview.png`).
- Artes de fundo (Aparência): `design/brand/backgrounds/` (5 opções; `build_thumbs.py` copia para o web e gera
  miniaturas). Contraste do texto sobre elas: `python3 scripts/background_contrast.py`.
- Tokens e tabela de contraste: `design/prototype/tokens.css`, [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md),
  verificados por `python3 scripts/wcag_contrast.py`.
- **Protótipo navegável** (HTML/CSS/JS puro): `design/prototype/index.html`. Feed segmentado por tipo,
  especialidade, origem e estado; cards de atores; perfil; telas de LGPD (consentimentos, seus dados,
  sessões, exclusão com carência, Encarregado). Capturas em claro/escuro e desktop/mobile em
  `design/prototype/shots/`, geradas com testes de fumaça por
  `PLAYWRIGHT_ROOT=$(npm root -g) node design/prototype/render.mjs`.

## Backend (`apps/core-api`)

```bash
docker compose up -d                                   # PostGIS 17, Valkey 9, Cassandra 5 (auditoria) e Garage (S3) com buckets
cd apps/core-api
mvn test                                               # unitários, sem Docker
mvn verify                                             # + *IT com Testcontainers (Docker: PostGIS, Valkey, Cassandra, Garage)
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8080/api/v1/ping
python3 ../../scripts/gen_schema.py                    # regenera schema.sql depois de uma migração nova
```

Fora dos perfis `dev`/`it`, `DE_APP_SECRET` (Base64, 32+ bytes) é obrigatório.

## Prontuário (`apps/clinical-api`)

Banco, usuário e segredo próprios (ADR-001). O navegador nunca fala com ela: a core-api recebe
`/api/v1/clinical/**`, valida sessão e CSRF e repassa com um token interno de 60 s (ADR-008).

```bash
docker compose up -d                                   # o Compose já cria o banco doctor_explorer_clinical e o usuário clinical
cd apps/clinical-api
mvn verify                                             # unitários + EhrIT, DocumentIT e FhirValidationIT (Testcontainers PostgreSQL)
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8081/actuator/health
```

Fora do perfil `dev`, `DE_CLINICAL_SECRET` (Base64, 32+ bytes) é obrigatório; a core-api acha a clinical-api em
`DE_CLINICAL_BASE_URL` (padrão `http://localhost:8081`).

Receitas (RF09): `DE_SIGNATURE_PROVIDER` escolhe quem assina. O padrão é `unavailable` (assinar falha com a causa
e o rascunho fica intacto) até o provedor ICP-Brasil em nuvem ser contratado (ADR-007); os perfis `dev` e `it`
usam `simulated`, uma AC e uma TSA "ICP-Brasil SIMULADA" derivadas do segredo, marcadas "sem validade jurídica"
no PDF e no validador. `DE_PUBLIC_BASE_URL` (padrão `http://localhost:3000`) vai no QR e no link do paciente.

## Web (`apps/web`)

Next.js 16 (App Router) + Tailwind v4 com os tokens de `design/prototype/tokens.css` e os ícones de
`design/icons` (o CI confere que as cópias batem). O navegador só fala com o próprio host: `/api/*` é
reescrito para a core-api, então a sessão opaca e o `XSRF-TOKEN` ficam no mesmo site. **Nunca** token
em localStorage; o único item guardado no aparelho é o tema.

```bash
pnpm install                                   # workspace na raiz (pnpm 10)
cd apps/web
API_ORIGIN=http://localhost:8080 pnpm dev      # http://localhost:3000 → /login
pnpm check                                     # typecheck + eslint + vitest + next build (o mesmo do CI)
```

Telas: `/login` (senha → 2FA → reativação), `/cadastro`, `/verificar-email`, `/feed` (quatro eixos na
URL), `/casos/novo`, `/casos/[id]`, `/grupos`, `/perfil` (+ selo CRM/RQE), `/perfil/[id]`,
`/privacidade`, `/seguranca`, `/instituicoes`, `/instituicoes/nova`, `/instituicoes/[id]`, `/instituicoes/[id]/gestao`
(gestores), `/moderacao` (moderadores com 2FA), `/aparencia` (arte de fundo, intensidade, tema, tamanho do texto). No perfil `dev` da API, `POST /api/v1/dev/roles/{role}` e
`POST /api/v1/dev/credentials/manual-review` ajudam a testar papéis e a fila sem mexer no banco.

## CI

`.github/workflows/ci.yml`: `mvn verify` da core-api e da clinical-api em JDK 21 e 25 · `apps/web` (typecheck, lint, vitest, build) · `web-e2e` (navegador real contra as duas APIs reais com o Compose) · **Docs por RF** (`scripts/docs_por_rf.py`:
doc do RF atualizado no mesmo commit em que seu código muda + todo Mermaid renderiza) · contraste e
protótipo · CodeQL · Gitleaks · Trivy · SBOM CycloneDX · Dependabot.

## Stack (resumo)

Java 21 · Spring Boot 4.1 · Spring Security 7 · Spring Session + Valkey 9 · PostgreSQL 17 + PostGIS ·
Cassandra 5 · S3 · Flyway · Testcontainers · Next.js 16 · Expo 58 · Tailwind v4 · Tokens DTCG. Detalhes e
exceções em [docs/PESQUISA_TECNOLOGIAS.md](docs/PESQUISA_TECNOLOGIAS.md).

## Validar os diagramas localmente

```bash
npx -y @mermaid-js/mermaid-cli@11 -i docs/rf/RF01-identidade-e-sessao.md -o /tmp/rf01.md
```
