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
| [ADR-012](#adr-012--dcm4che-e-tesseract-na-anonimização) | dcm4che e Tesseract na anonimização | proposto |

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

## ADR-012 · dcm4che e Tesseract na anonimização

**Decisão.** **dcm4che** para desidentificação DICOM (PS3.15 Anexo E) e **Tesseract** (por) para OCR de
texto gravado em imagem; detecção de rosto com modelo leve embarcado (sem enviar imagem a terceiros).
Tudo roda em worker isolado, só em memória.
