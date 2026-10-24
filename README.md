<p align="center"><img src="design/brand/logo-doctor-explorer.svg" width="320" alt="Doctor Explorer"></p>

# Doctor Explorer

Rede social profissional **fechada e verificada** para médicos, profissionais de saúde e redes
hospitalares. Casos clínicos anonimizados, chat e vídeo com criptografia ponta a ponta, nota SOAP no
prontuário, receita e pedido de exame com ICP-Brasil, diretório hospitalar com RBAC. Conformidade com
**CFM, LGPD e Anvisa** desde o primeiro dia.

Estado atual: **MVP em construção**. `apps/core-api` cobre RF01–RF04, RF10–RF12 (identidade, selo
CRM/RQE, perfil, grupos e feed, LGPD, diretório hospitalar, RBAC); `apps/web` é a interface real em
Next.js sobre essa API. Ordem de entrega em
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
docker compose up -d                                   # PostGIS 17, Valkey 9, Garage (+ `--profile full` para Cassandra)
cd apps/core-api
mvn test                                               # unitários, sem Docker
mvn verify                                             # + *IT com Testcontainers
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8080/api/v1/ping
python3 ../../scripts/gen_schema.py                    # regenera schema.sql depois de uma migração nova
```

Fora dos perfis `dev`/`it`, `DE_APP_SECRET` (Base64, 32+ bytes) é obrigatório. A `clinical-api` (PEP,
prescrição, FHIR) entra na fase 9 com banco e chaves separados.

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

`.github/workflows/ci.yml`: `mvn verify` em JDK 21 e 25 · `apps/web` (typecheck, lint, vitest, build) · `web-e2e` (navegador real contra a API real com PostGIS e Valkey) · **Docs por RF** (`scripts/docs_por_rf.py`:
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
