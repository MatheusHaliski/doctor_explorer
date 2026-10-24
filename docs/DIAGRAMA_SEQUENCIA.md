# Diagramas de sequência transversais

As sequências por RF estão em cada `docs/rf/RFnn-*.md`. Aqui ficam as duas que atravessam todos os
serviços.

## Requisição autenticada passando pela camada de QoS

```mermaid
sequenceDiagram
    autonumber
    participant W as Web / Mobile
    participant E as edge (TLS 1.3 · WAF)
    participant S as Sessão (Valkey)
    participant T as Token interno (60 s)
    participant C as core-api ou clinical-api
    participant A as AccessService
    participant AU as Auditoria
    W->>E: GET /api/v1/... (cookie __Host-de_session + X-XSRF-TOKEN se escrita)
    E->>E: rate limit por IP · WAF
    E->>S: valida sessão, renova ocioso (60 min), checa absoluto (24 h)
    alt sessão inválida
      E-->>W: 401 Problem Details
    else válida
      E->>T: emite token interno {userId, roles, seal, mfa, exp+60s}
      E->>C: encaminha com token (mTLS)
      C->>A: decide(ctx, recurso, ação)
      alt negado
        A->>AU: access.denied
        C-->>W: 403 (motivo em pt-BR) ou 404 (dono)
      else permitido
        C->>C: lógica do caso de uso
        C->>AU: AuditEvent (se ação relevante)
        C-->>W: 2xx
      end
    end
```

## Jornada ponta a ponta: do caso no fórum à receita assinada

```mermaid
sequenceDiagram
    autonumber
    actor A as Dra. Ana (assistente)
    participant F as Fórum (RF04/05)
    actor B as Dr. Bruno (consultado)
    participant CH as Chat E2EE (RF06)
    participant PEP as clinical-api (RF08)
    participant RX as Prescrição (RF09)
    participant ICP as Provedor ICP-Brasil
    actor P as Paciente
    A->>F: publica caso anonimizado
    B->>F: parecer
    A->>CH: tira dúvida com Bruno (E2EE)
    CH-->>A: sugestão "Registrar no prontuário"
    A->>PEP: nota SOAP vinculando o paciente real + link opaco do post
    PEP-->>A: nota assinada
    A->>RX: receita a partir do Plano da nota
    RX->>ICP: PAdES
    ICP-->>RX: assinada
    RX-->>P: link único (7 dias)
    Note over F,PEP: o fórum nunca conhece o paciente e o PEP guarda só o id do post
```
