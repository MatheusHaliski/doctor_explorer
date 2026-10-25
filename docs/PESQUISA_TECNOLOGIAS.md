# Pesquisa de tecnologias · ADRs

Toda tecnologia fora da stack de referência (Orange Social) entra aqui como um ADR. Formato: contexto,
decisão, alternativas, consequências, status. Status possíveis: **proposto · aceito · substituído**.

| ADR | Decisão | Status |
|---|---|---|
| [ADR-001](#adr-001--monólito-modular-com-clinical-api-separada) | Monólito modular + `clinical-api` separada | proposto |
| [ADR-002](#adr-002--barramento-de-eventos-por-instituição) | Barramento de eventos por instituição (outbox → streams) | proposto |
| [ADR-003](#adr-003--openehr-como-modelo-interno-fhir-como-fachada) | openEHR interno, FHIR R4 como fachada | proposto |
| [ADR-004](#adr-004--hapi-fhir) | HAPI FHIR | proposto |
| [ADR-005](#adr-005--servidor-de-terminologia) | Servidor de terminologia (CID-10, LOINC, SNOMED CT) | proposto |
| [ADR-006](#adr-006--webrtc-com-sfu-e-e2ee-sframe) | WebRTC com SFU + E2EE SFrame | proposto |
| [ADR-007](#adr-007--assinatura-icp-brasil-em-nuvem) | Assinatura ICP-Brasil via provedor em nuvem | proposto |
| [ADR-008](#adr-008--sessão-opaca-no-cliente-token-interno-curto) | Sessão opaca no cliente; token interno de 60 s entre serviços | proposto |
| [ADR-009](#adr-009--fonte-de-verificação-de-crmrqe) | Fonte de verificação de CRM/RQE | proposto |
| [ADR-010](#adr-010--mls-rfc-9420-para-chat-e2ee) | MLS (RFC 9420) para chat E2EE | proposto |
| [ADR-011](#adr-011--infraestrutura-gerenciada-na-aws-sa-east-1) | Infraestrutura gerenciada na AWS sa-east-1 | proposto |
| [ADR-012](#adr-012--dcm4che-e-tesseract-na-anonimização) | dcm4che e Tesseract na anonimização | aceito |
| [ADR-013](#adr-013--busca-por-raio-haversine-agora-postgis-depois) | Busca por raio: Haversine agora, PostGIS depois | aceito |
| [ADR-014](#adr-014--auditoria-encadeada-na-cassandra) | Auditoria encadeada na Cassandra, exportação assinada com Ed25519 | aceito |
| [ADR-015](#adr-015--garage-como-s3-de-dev-e-de-teste) | Garage como S3 de dev e de teste | aceito |

---

## ADR-001 · Monólito modular com `clinical-api` separada

**Contexto.** O prompt mestre pede SOA/microserviços com ESB hierárquico. A stack de referência é um único
Spring Boot. Um time pequeno não sustenta dezenas de serviços, mas o PEP tem retenção de 20 anos,
superfície de ataque e certificação (SBIS) diferentes do resto.

**Decisão.** Dois serviços Spring Boot 4.1: `core-api` (identidade, social, diretório, LGPD) e
`clinical-api` (PEP, prescrição, FHIR), com bancos, credenciais e chaves separados. Dentro de cada um,
módulos com fronteiras verificadas por **ArchUnit**. `rtc` separado por perfil de carga. `cds` reservado.

**Alternativas.** (a) Microserviços desde o início: custo operacional alto cedo demais. (b) Monólito único:
mistura chaves e retenções do PEP com dados sociais descartáveis.

**Consequências.** Dois pipelines de deploy; chamadas entre serviços por mTLS; a saga de exclusão (RF10)
não toca a `clinical-api`.

## ADR-002 · Barramento de eventos por instituição

**Contexto.** O "ESB hierárquico" do prompt mestre resolve gargalo no barramento central.

**Decisão.** Padrão *transactional outbox* no PostgreSQL + **Valkey Streams**, com um stream por
instituição (`events:{institutionId}`) e um stream central só para eventos entre instituições. Troca para
Kafka/Redpanda quando a carga passar de ~5 mil eventos/s (ADR futuro).

**Alternativas.** Kafka desde o início (operação pesada); chamadas síncronas (acoplamento).

**Consequências.** Entrega *at-least-once*: consumidores idempotentes.

## ADR-003 · openEHR como modelo interno, FHIR como fachada

**Decisão.** Persistência do PEP em Compositions openEHR (arquétipos do CKM, templates próprios) e
exposição por FHIR R4 (perfis da RNDS). Mapeamento documentado por arquétipo.

**Alternativas.** Só FHIR como persistência (mais simples, mas sem a semântica de arquétipos pedida); só
openEHR (sem interoperabilidade com RNDS).

**Consequências.** Camada de mapeamento com testes de ida e volta; EHRbase avaliado como repositório
openEHR pronto (**⚖️ licença e operação**).

## ADR-004 · HAPI FHIR

**Decisão.** Biblioteca HAPI FHIR (estruturas R4 + validador) dentro da `clinical-api`, sem o servidor JPA
completo. Validação contra perfis BR no CI.

## ADR-005 · Servidor de terminologia

**Decisão.** CID-10 e LOINC carregados no PostgreSQL com busca por texto. SNOMED CT via servidor de
terminologia FHIR (Snowstorm ou Ontoserver) **só depois da licença** **⚖️**. Interface única
`TerminologyService` para trocar o backend.

## ADR-006 · WebRTC com SFU e E2EE SFrame

**Contexto.** DTLS-SRTP protege só até o servidor de mídia. O requisito é ponta a ponta.

**Decisão.** SFU (**mediasoup** ou **LiveKit**, a decidir por POC) + *insertable streams* com **SFrame**;
chave derivada da epoch MLS da conversa. coturn para relay.

**Alternativas.** Mesh P2P (não escala além de 4); MCU (precisa decifrar).

**Consequências.** Safari e navegadores antigos têm suporte parcial a insertable streams: lista de
navegadores suportados publicada; mobile usa libwebrtc com frame transformer.

## ADR-007 · Assinatura ICP-Brasil em nuvem

**Decisão.** Integração com provedor de assinatura em nuvem homologado pelo ITI (certificado A3 em HSM do
provedor; o médico autoriza pelo app do provedor). Padrão **PAdES** com carimbo de tempo. Interface
`SignatureProviderPort` para trocar de provedor.

**Alternativas.** Token/cartão A3 local (exige driver, inviável no mobile); A1 guardado por nós (risco
inaceitável).

**Consequências.** Dependência externa na hora da assinatura: estado "Assinando" com retry e mensagem
clara em falha.

## ADR-008 · Sessão opaca no cliente, token interno curto

**Contexto.** O prompt mestre pede "JWT curto com 2FA"; a stack de referência proíbe JWT no cliente.

**Decisão.** Cliente: sessão opaca (Spring Session no Valkey). Entre `edge` e serviços: token interno
assinado de **60 s** com escopo da requisição (não é revogável, mas vive só 60 s e nunca sai da rede
interna). 2FA e sudo ficam na sessão.

## ADR-009 · Fonte de verificação de CRM/RQE

**Decisão.** Consulta à fonte oficial do CFM (serviço de dados ou busca pública de médicos), com adapter,
cache de 24 h, circuit breaker e fila de retry. Resultado guardado com hash da resposta. Revisão manual
quando a fonte não responde em 24 h. **⚖️ validar** condições de uso e eventual convênio com o CFM.

## ADR-010 · MLS (RFC 9420) para chat E2EE

**Decisão.** MLS via **OpenMLS** (Rust) compilado para WASM (web) e biblioteca nativa (mobile). Servidor
só como Delivery Service. Backup de chave opcional protegido por frase.

**Alternativas.** Signal Protocol (grupos grandes são caros); Matrix/Olm (mais ecossistema, menos controle).

**Consequências.** Sem moderação de conteúdo no servidor: denúncia pelo cliente. Nova tecnologia (Rust/WASM)
na stack: revisão criptográfica externa obrigatória.

## ADR-011 · Infraestrutura gerenciada na AWS sa-east-1

**Decisão.** RDS PostgreSQL 17 Multi-AZ com réplicas de leitura (PostGIS habilitado), ElastiCache Valkey em
cluster, Cassandra em Keyspaces ou autogerido em 3 AZs (decidir por custo), S3 com Object Lock, KMS com
HSM, ECS Fargate ou EKS para os contêineres Jib. Backup secundário em outra região **⚖️ transferência
internacional**.

## ADR-013 · Busca por raio: Haversine agora, PostGIS depois

**Contexto.** A stack prevê PostGIS para a busca por raio. O diretório do MVP tem centenas de
instituições, e os ambientes de desenvolvimento restritos nem sempre têm a extensão instalada.

**Decisão.** `institution.lat/lng` em `double precision` e distância por Haversine em SQL puro
(`Institutions.search`), que roda em qualquer PostgreSQL. O Compose e o CI já usam a imagem
`postgis/postgis:17-3.5`; quando o diretório passar de alguns milhares de linhas ou a busca por raio
entrar no caminho crítico, uma migração adiciona `location geography(Point, 4326)` gerada de `lat/lng`,
índice GiST e `ST_DWithin`, sem mudar a API.

**Consequências.** Varredura completa na busca por raio (aceitável no MVP); a troca é só de migração e
query.

## ADR-012 · dcm4che e Tesseract na anonimização

**Decisão.** **dcm4che** para desidentificação DICOM (PS3.15 Anexo E) e **Tesseract** (por) para OCR de
texto gravado em imagem; detecção de rosto com modelo leve embarcado (sem enviar imagem a terceiros).
Tudo roda em worker isolado, só em memória.

**Como ficou (RF05).** dcm4che 5.35.2 (`dcm4che-deident` + leitor ImageIO DICOM, repositório Maven próprio do
projeto); Tesseract 5 pela linha de comando (`stdin → tsv`, `por+eng`), sem JNI; libheif (`heif-info`,
`heif-convert`) para HEIC, com temporários em `/dev/shm`; detecção de rosto com OpenCV 4.9 (openpnp, nativo
embarcado no jar) e a cascata Haar frontal versionada em `resources/media`; metadata-extractor só para listar o
que foi removido. A saída é sempre reconstruída a partir dos pixels. Os binários de sistema ficam na imagem base
`infra/images/core-api-base`; o CI instala os mesmos pacotes. Ferramenta ausente ou com erro → mídia `FALHOU`
(falha fechada). Trade-off aceito: Haar perde rostos de perfil e muito pequenos; por isso a revisão do autor é
obrigatória e a tarja manual existe. Um detector DNN (YuNet) é o próximo passo se a avaliação de usabilidade
mostrar rostos escapando.

## ADR-014 · Auditoria encadeada na Cassandra

**Contexto.** O RF13 pede trilha de auditoria append-only, filtrável, exportável e à prova de adulteração, com
acesso recortado por papel e por árvore de instituições. A stack de referência prevê Cassandra para dados de
escrita intensa.

**Decisão.** Tabela `de_audit.audit_event` particionada por (escopo, dia), com cadeia de hash por escopo
(`platform` recebe tudo; `inst:{id}` recebe os eventos daquela instituição). Para anexar: lê a cabeça, grava o
evento com `seq + 1` e avança `audit_chain_head` por LWT (`IF seq = ?`); se outra instância venceu, o provisório
é apagado e a operação recomeça. A leitura recalcula a cadeia do trecho e acusa alteração, buraco ou elo
quebrado. Exportação em CSV assinada com Ed25519 (chave derivada do segredo da aplicação por HKDF), guardada no S3
e entregue por URL pré-assinada de 15 min; a chave pública é publicada sem login para o auditor conferir com
OpenSSL. `AuditLog.record` falha junto com a ação: ação sensível sem rastro não acontece.

**Alternativas.** (a) Tabela append-only no PostgreSQL: mais simples, mas concorre com o banco transacional e
não escala com chat/chamadas. (b) Serviço externo de log (SIEM): bom complemento, mas sem o controle de recorte
por papel e de exportação assinada.

**Consequências.** LWT custa uma ida extra de consenso por evento (aceitável para auditoria). Uma queda entre
gravar o evento e avançar a cabeça deixa um provisório com `seq` repetido, que a verificação aponta. Filtros por
ator/ação são feitos na aplicação depois de ler as partições do período (até 31 dias por consulta); índices
secundários entram se o volume pedir. Em produção: replicação `NetworkTopologyStrategy` com 3 réplicas (ADR-011)
e Object Lock no bucket de exportações.

## ADR-015 · Garage como S3 de dev e de teste

**Contexto.** Exportações de auditoria (RF13) e mídia (RF05) precisam de S3 com URL pré-assinada nos testes de
integração. O MinIO deixou de publicar imagens comunitárias no Docker Hub, e o Compose já usava Garage.

**Decisão.** Garage em dev (Compose, `infra/garage/init.sh`) e nos `*IT` (`support/GarageContainer`, mesma Admin
API v2). Ligações em IPv4 (`0.0.0.0`), porque runners sem IPv6 derrubam o processo ao abrir `[::]`. O cliente S3
calcula checksum só quando a operação exige e não envia em blocos (`WHEN_REQUIRED`, `chunkedEncoding=false`),
porque o Garage recusa a assinatura de payload padrão dos SDKs recentes; na AWS isso também é válido. SSE-S3 fica
ligado só em produção (Garage sem KMS recusa o cabeçalho).

**Consequências.** Um S3 só em dev, teste e Compose; produção continua na AWS sa-east-1.
