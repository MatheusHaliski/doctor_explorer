---
rf: RF10
titulo: Consentimentos, painel do titular e exclusão de conta (LGPD)
status: especificado
cards: [US22, US23, US24]
paths:
  - apps/core-api/src/main/java/**/privacy/**
  - apps/web/app/(app)/privacidade/**
---

# RF10 · Consentimentos, painel do titular e exclusão de conta (LGPD)

## Critérios de aceite

1. **Consentimentos:** um toggle por finalidade (localização, lembretes, recomendação de conteúdo,
   comunicação institucional), **todos desligados por padrão**. Revogar usa o mesmo controle. Histórico
   completo em `consent_event` (quem, quando, versão do texto, IP).
2. **Seus dados:** ver o que guardamos por categoria, **corrigir** cadastro, **exportar** (JSON versionado
   + ZIP cifrado por URL pré-assinada, pronto em até 24 h), e pedir informação sobre compartilhamento
   (lista de operadores).
3. **Excluir conta:** `alertdialog` com consequências, data final da carência (30 dias), "Baixar uma
   cópia", caixa "Entendo" + senha + TOTP; botão só habilita depois da caixa. Explica que **registros
   clínicos ficam guardados por 20 anos** por obrigação legal.
4. **Reativar:** oferta no login durante a carência.
5. **Eliminação:** saga idempotente em job diário (03:15): anonimiza o PostgreSQL social, remove objetos
   do S3 **depois do commit**, limpa Cassandra e Valkey e destrói a **DEK social** (crypto-shredding).
   Recibo sem dado pessoal no log.
6. **Canal do Encarregado:** formulário autenticado ou e-mail, protocolo, prazo de resposta de 15 dias
   **⚖️ validar**.
7. Dado de saúde nunca vai em log, URL, analytics ou push.

## Base legal

LGPD Art. 7º, I; Art. 8º §5º; Art. 9º; Art. 11, I; Art. 16, I; Art. 18 (I a IX); Art. 37; Art. 41; Art. 46.

## Máquina de estados (conta, visão LGPD)

```mermaid
stateDiagram-v2
    ACTIVE --> PENDING_DELETION: pedido (sudo)
    PENDING_DELETION --> ACTIVE: reativar em até 30 dias
    PENDING_DELETION --> ERASING: job diário, carência vencida
    ERASING --> ERASING: etapa da saga (idempotente)
    ERASING --> ANONYMOUS_LGPD_DELETED: todas as etapas ok + DEK destruída
    ERASING --> ERASE_FAILED: etapa falhou 5 vezes
    ERASE_FAILED --> ERASING: operador reprocessa
    ANONYMOUS_LGPD_DELETED --> [*]
```

## Classes

```mermaid
classDiagram
    class ConsentPurpose {
      <<enumeration>>
      LOCATION
      REMINDERS
      CONTENT_RECOMMENDATION
      INSTITUTIONAL_COMMUNICATION
    }
    class ConsentEvent {
      +UUID id
      +UUID userId
      +ConsentPurpose purpose
      +boolean granted
      +String textVersion
      +Instant at
      +String ipHash
    }
    class DataExportRequest {
      +UUID id
      +ExportState state
      +String s3Key
      +Instant readyAt
      +Instant expiresAt
    }
    class DeletionRequest {
      +UUID id
      +Instant requestedAt
      +Instant graceEndsAt
      +DeletionState state
    }
    class ErasureSaga {
      +run(userId)
      -anonymizePostgres()
      -deleteS3Objects()
      -purgeCassandra()
      -purgeValkey()
      -shredUserKey()
    }
    class UserKey {
      +UUID userId
      +byte[] wrappedDek
      +Instant destroyedAt
    }
    class DpoTicket {
      +UUID id
      +String protocol
      +TicketType type
      +Instant dueAt
    }
    class PrivacyService {
      +setConsent(purpose, granted)
      +requestExport()
      +requestDeletion()
      +reactivate()
    }
    PrivacyService ..> ConsentEvent
    PrivacyService ..> DataExportRequest
    PrivacyService ..> DeletionRequest
    ErasureSaga ..> UserKey
    ConsentEvent --> ConsentPurpose
```

## Sequência: exclusão com carência

```mermaid
sequenceDiagram
    autonumber
    actor U as Titular
    participant W as Web
    participant P as core-api · PrivacyService
    participant PG as PostgreSQL
    participant J as ErasureJob (03:15)
    participant S3 as S3
    participant CS as Cassandra
    participant VK as Valkey
    participant K as KMS
    U->>W: Excluir conta → marca "Entendo" + senha + TOTP
    W->>P: POST /me/deletion (sudo)
    P->>PG: status = PENDING_DELETION, graceEndsAt = +30 d
    P-->>W: data final da carência + oferta de exportar
    Note over U,P: durante 30 dias, login oferece Reativar
    J->>PG: busca contas com carência vencida
    J->>PG: anonimiza perfil, posts, comentários (commit)
    J->>S3: apaga objetos do usuário
    J->>CS: apaga mensagens e notificações
    J->>VK: encerra sessões e limpa chaves
    J->>K: destrói DEK social (crypto-shredding)
    J->>PG: status = ANONYMOUS_LGPD_DELETED
    J-->>J: recibo no log sem dado pessoal
```

## Componentes

```mermaid
flowchart LR
    UI[Web: Privacidade · Consentimentos · Seus dados · Excluir · Encarregado] --> PC[PrivacyController]
    PC --> PS[PrivacyService]
    PS --> CE[(consent_event)]
    PS --> EX[ExportWorker → JSON + ZIP cifrado] --> S3[(S3 · URL pré-assinada)]
    PS --> DR[(deletion_request)]
    JOB[ErasureJob · @Scheduled 03:15] --> SAGA[ErasureSaga]
    SAGA --> PG[(PostgreSQL)] & S3 & CS[(Cassandra)] & VK[(Valkey)] & KMS[[KMS]]
    PS --> DPO[DpoTicketService]
    PS & SAGA --> AU[Auditoria]
```

## Atividades: saga de eliminação

```mermaid
flowchart TD
    A([Job diário 03:15]) --> B[Lista contas com carência vencida]
    B --> C{Há contas?}
    C -- não --> Z([Fim])
    C -- sim --> D[Para cada conta: marca ERASING]
    D --> E[Anonimiza PostgreSQL social · commit]
    E --> F[Apaga objetos S3 do usuário]
    F --> G[Limpa Cassandra · mensagens e notificações]
    G --> H[Limpa Valkey · sessões]
    H --> I[Destrói DEK social no KMS]
    I --> J[Marca ANONYMOUS_LGPD_DELETED]
    J --> K[Recibo sem dado pessoal]
    K --> C
    E & F & G & H & I -. falha .-> R{Tentativas < 5?}
    R -- sim --> D
    R -- não --> X[ERASE_FAILED · alerta ao operador]
```

Registros clínicos (RF08, RF09) **não** entram na saga: ficam no cofre clínico, com a chave do cofre, por
20 anos.

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Migração | `V1` (consent_event, terms_acceptance, user_key) | ⏳ |
| Serviço | `PrivacyService`, `ErasureSaga`, `ExportWorker` | ⏳ |
| Job | `ErasureJob` | ⏳ |
| Web | telas de Privacidade | ⏳ |
| Testes | `DeletionSagaIT` (idempotência, falha no meio), `ConsentIT` | ⏳ |
