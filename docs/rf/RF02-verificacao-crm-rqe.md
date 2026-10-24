---
rf: RF02
titulo: Verificação de CRM/RQE e selo profissional
status: especificado
cards: [US05, US06]
paths:
  - apps/core-api/src/main/java/**/credentialing/**
---

# RF02 · Verificação de CRM/RQE e selo profissional

## Critérios de aceite

1. O médico informa CRM, UF e, para cada especialidade declarada, o RQE.
2. O sistema consulta a fonte oficial do CFM (ADR-009) e compara nome, situação ("Regular") e
   especialidades/RQE.
3. Se tudo confere, o selo vai para `VERIFIED`. Se houver divergência ou situação irregular, vai para
   `REJECTED` com o motivo em pt-BR. Se a fonte estiver fora do ar, fica `PENDING` e entra em fila de
   nova tentativa (até 24 h, depois vai para revisão manual pelo moderador).
4. Mudar nome, CRM ou especialidade de conta verificada volta o selo para `PENDING`.
5. Job diário reverifica todos os selos `VERIFIED`. Situação diferente de "Regular" suspende os recursos
   clínicos na hora e avisa o médico.
6. A evidência de cada consulta (data, fonte, hash da resposta, resultado) é guardada e auditada.
7. Selo exibido com **cor + ícone + texto** ("CRM verificado", "Em análise", "Não verificado").
8. Sem `VERIFIED`: não publica, não conversa, não prescreve e não aparece no diretório.

## Base legal

Lei 3.268/1957 (exercício da medicina exige inscrição no CRM) · Res. CFM sobre RQE ·
LGPD Art. 7º, II/IX (cumprimento de obrigação e legítimo interesse na prevenção a fraude) **⚖️ validar**.

## Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> NONE
    NONE --> PENDING: submeter(CRM, UF, RQE[])
    PENDING --> VERIFIED: fonte confere
    PENDING --> REJECTED: divergência / irregular
    PENDING --> PENDING: fonte indisponível (retry)
    PENDING --> MANUAL_REVIEW: 24 h sem resposta
    MANUAL_REVIEW --> VERIFIED: moderador aprova (com evidência)
    MANUAL_REVIEW --> REJECTED: moderador recusa
    REJECTED --> PENDING: reenvio
    VERIFIED --> PENDING: muda nome / CRM / especialidade
    VERIFIED --> REVOKED: reverificação: situação ≠ Regular
    REVOKED --> PENDING: situação regularizada
```

## Classes

```mermaid
classDiagram
    class ProfessionalProfile {
      +UUID id
      +String crmNumber
      +String crmUf
      +SealStatus seal
      +List~SpecialtyClaim~ specialties
      +submitForVerification()
      +onIdentityChanged()
    }
    class SealStatus {
      <<enumeration>>
      NONE
      PENDING
      MANUAL_REVIEW
      VERIFIED
      REJECTED
      REVOKED
    }
    class SpecialtyClaim {
      +String specialtyCode
      +String rqe
      +boolean confirmed
    }
    class CredentialCheck {
      +UUID id
      +String source
      +Instant checkedAt
      +String responseHash
      +CheckOutcome outcome
      +String reasonPtBr
    }
    class CfmRegistryPort {
      <<interface>>
      +lookup(crm, uf) CfmRecord
    }
    class CfmRegistryAdapter
    class CredentialingService {
      +verify(profileId)
      +reverifyAll()
    }
    ProfessionalProfile --> SealStatus
    ProfessionalProfile "1" *-- "*" SpecialtyClaim
    ProfessionalProfile "1" --> "*" CredentialCheck
    CredentialingService ..> CfmRegistryPort
    CfmRegistryAdapter ..|> CfmRegistryPort
    CredentialingService ..> ProfessionalProfile
```

## Sequência

```mermaid
sequenceDiagram
    autonumber
    actor M as Médico
    participant A as core-api · CredentialingService
    participant Q as Fila (outbox)
    participant C as CFM (fonte oficial)
    participant P as PostgreSQL
    participant AU as Auditoria
    M->>A: POST /credentials (CRM, UF, RQE[])
    A->>P: selo = PENDING
    A->>Q: evento VerificationRequested
    A-->>M: 202 "Em análise"
    Q->>A: consome evento
    A->>C: lookup(CRM, UF)
    alt fonte responde
      C-->>A: situação, nome, especialidades/RQE
      A->>A: compara (nome normalizado, situação, RQE)
      A->>P: VERIFIED ou REJECTED + CredentialCheck
    else timeout / erro
      A->>Q: reagenda (backoff exponencial)
    end
    A->>AU: AuditEvent(credential.check)
    A-->>M: notificação (sem dado sensível)
```

## Componentes

```mermaid
flowchart LR
    UI[Web: tela Verificação] --> CC[CredentialController]
    CC --> CS[CredentialingService]
    CS --> PORT{{CfmRegistryPort}}
    PORT --> AD[CfmRegistryAdapter<br/>timeout · circuit breaker · cache 24 h]
    AD --> CFM[(CFM)]
    CS --> OB[(Outbox)]
    JOB[ReverificationJob · @Scheduled 02:30] --> CS
    CS --> PG[(PostgreSQL)]
    CS --> AU[Auditoria]
    MOD[Painel do moderador] --> CS
```

## Atividades

```mermaid
flowchart TD
    A([Selo NONE]) --> B[Médico informa CRM/UF e RQEs]
    B --> C[Consulta fonte oficial]
    C --> D{Respondeu?}
    D -- não --> E{Passaram 24 h?}
    E -- não --> C
    E -- sim --> F[Revisão manual pelo moderador] --> G
    D -- sim --> G{Situação Regular e nome confere?}
    G -- não --> H[REJECTED + motivo] --> I([Médico corrige e reenvia])
    G -- sim --> J{Todos os RQE conferem?}
    J -- não --> K[VERIFIED só nas especialidades confirmadas + aviso]
    J -- sim --> L[VERIFIED]
    K & L --> M[Libera publicar, conversar, prescrever, diretório]
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Migração | `V2__credentialing.sql` | ⏳ |
| Domínio | `ProfessionalProfile`, `SealStatus`, `CredentialCheck` | ⏳ |
| Integração | `CfmRegistryAdapter` (ADR-009) | ⏳ |
| Job | `ReverificationJob` | ⏳ |
| Testes | `CredentialingIT` com stub WireMock do CFM | ⏳ |
