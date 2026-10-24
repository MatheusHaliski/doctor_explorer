<p align="center"><img src="design/brand/logo-doctor-explorer.svg" width="320" alt="Doctor Explorer"></p>

# Doctor Explorer

Rede social profissional **fechada e verificada** para médicos, profissionais de saúde e redes
hospitalares. Casos clínicos anonimizados, chat e vídeo com criptografia ponta a ponta, nota SOAP no
prontuário, receita e pedido de exame com ICP-Brasil, diretório hospitalar com RBAC. Conformidade com
**CFM, LGPD e Anvisa** desde o primeiro dia.

Estado atual: **especificação**. Nenhum código de aplicação ainda; a ordem de entrega está em
[docs/ESPECIFICACAO_TECNICA.md §11](docs/ESPECIFICACAO_TECNICA.md#11-plano-de-entrega).

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

## CI

`.github/workflows/ci.yml`: `mvn verify` em JDK 21 e 25 · **Docs por RF** (`scripts/docs_por_rf.py`:
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
