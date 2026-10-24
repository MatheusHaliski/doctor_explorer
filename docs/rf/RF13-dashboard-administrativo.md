---
rf: RF13
titulo: Dashboard administrativo (engajamento, auditoria, qualidade)
status: especificado
cards: [US28, US29]
paths:
  - apps/core-api/src/main/java/**/admin/**
  - apps/web/app/(admin)/**
---

# RF13 · Dashboard administrativo (engajamento, auditoria, qualidade)

## Critérios de aceite

1. Visível para `HOSPITAL_ADMIN`, `NETWORK_ADMIN`, `AUDITOR` (na sua árvore), `MODERATOR`, `ADMIN` e
   `DPO` (recorte de privacidade). Exige 2FA.
2. **Engajamento:** usuários ativos, casos publicados, pareceres, tempo até o 1º parecer, chamadas por
   semana. Tudo **agregado**, com célula mínima de **10 pessoas** (k-anonimato), sem dado de saúde e sem
   drill-down para indivíduo.
3. **Auditoria:** log filtrável por ator, ação, recurso, período; exportável em CSV assinado. Eventos
   clínicos mostram o id do paciente só para `AUDITOR`/`DPO` com justificativa registrada.
4. **Qualidade:** selos pendentes e tempo médio de verificação, incidentes de privacidade abertos,
   problemas de usabilidade por severidade (planilha da avaliação heurística), disponibilidade e p95.
5. Gráficos seguem o Design System (cores de série acessíveis, nunca só cor para distinguir, tabela de
   dados alternativa para leitor de tela).
6. Cada acesso ao dashboard de auditoria gera `AuditEvent` (auditoria da auditoria).

## Base legal

LGPD Art. 37 (registro das operações), Art. 46, Art. 50 (boas práticas e governança) · Res. CFM 1.821/2007
(auditoria do PEP) · ONA/acreditação **⚖️ opcional**.

## Máquina de estados (exportação de auditoria)

```mermaid
stateDiagram-v2
    [*] --> Solicitada: filtro + justificativa
    Solicitada --> Gerando: worker
    Gerando --> Pronta: CSV + assinatura + hash
    Gerando --> Falhou
    Pronta --> Baixada: URL pré-assinada 15 min
    Pronta --> Expirada: 24 h
    Baixada --> [*]
    Expirada --> [*]
    Falhou --> [*]
```

## Classes

```mermaid
classDiagram
    class MetricsService {
      +engagement(scope, period) EngagementReport
      +quality(scope, period) QualityReport
    }
    class EngagementReport {
      +int activeUsers
      +int casesPublished
      +int opinions
      +Duration medianTimeToFirstOpinion
      +int callsPerWeek
    }
    class KAnonymityGuard {
      +int minCell = 10
      +suppress(Report) Report
    }
    class AuditQuery {
      +UUID actorId
      +String action
      +String resourceType
      +Instant from
      +Instant to
      +String justification
    }
    class AuditEvent {
      +UUID id
      +Instant at
      +UUID actorId
      +String action
      +String resourceRef
      +String outcome
      +String prevHash
      +String hash
    }
    class AuditExport {
      +UUID id
      +ExportState state
      +String s3Key
      +String sha256
      +String signature
    }
    class AuditService {
      +search(AuditQuery) Page~AuditEvent~
      +export(AuditQuery) AuditExport
    }
    MetricsService ..> KAnonymityGuard
    MetricsService ..> EngagementReport
    AuditService ..> AuditEvent
    AuditService ..> AuditExport
    AuditService ..> AuditQuery
```

## Sequência: consultar auditoria

```mermaid
sequenceDiagram
    autonumber
    actor AU as Auditor do Hospital X
    participant W as Web (admin)
    participant A as core-api · AuditService
    participant AC as AccessService (RF12)
    participant CS as Cassandra (append-only)
    participant L as Log de auditoria
    AU->>W: filtra ação=composition.sign, período, justificativa
    W->>A: GET /admin/audit?...
    A->>AC: check(ctx, hospitalX, VIEW_AUDIT)
    AC-->>A: permitido (árvore de X)
    A->>L: AuditEvent(audit.view, justificativa)
    A->>CS: consulta por partição (instituição, dia)
    CS-->>A: eventos
    A->>A: verifica cadeia de hash
    A-->>W: página + indicador "cadeia íntegra"
```

## Componentes

```mermaid
flowchart LR
    UI[Web admin: Engajamento · Auditoria · Qualidade] --> AdC[AdminController]
    AdC --> MS[MetricsService] --> KG[KAnonymityGuard]
    MS --> PG[(PostgreSQL · réplica de leitura)]
    MS --> VK[(Valkey · agregados por hora)]
    AdC --> AS[AuditService]
    AS --> CS[(Cassandra · audit_event)]
    AS --> S3[(S3 Object Lock · exportações)]
    AdC --> QS[QualityService]
    QS --> CR[CredentialingService · RF02]
    QS --> HEU[(Planilha de avaliação heurística)]
    QS --> OTEL[OpenTelemetry · SLO]
    AdC --> RBAC[AccessService · RF12]
    AS & MS --> AU[Auditoria]
```

## Atividades: gerar relatório de engajamento

```mermaid
flowchart TD
    A([Abrir dashboard]) --> B{2FA e papel ok na árvore?}
    B -- não --> X[403 com motivo]
    B -- sim --> C[Escolhe escopo: rede / hospital / unidade e período]
    C --> D[Agrega métricas na réplica de leitura]
    D --> E{Alguma célula < 10 pessoas?}
    E -- sim --> F[Suprime a célula e marca como insuficiente]
    E -- não --> G
    F --> G[Renderiza gráficos + tabela acessível]
    G --> H{Exportar?}
    H -- sim --> I[CSV assinado · URL 15 min · AuditEvent]
    H -- não --> J([Fim])
    I --> J
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Serviço | `MetricsService`, `AuditService`, `QualityService` | ⏳ |
| Dados | Cassandra `audit_event` com hash encadeado | ⏳ |
| Web | páginas admin com gráficos acessíveis | ⏳ |
| Testes | `KAnonymityGuardTest`, `AuditChainIT`, `AdminAccessIT` | ⏳ |
