# Máquinas de estado transversais

As máquinas por RF estão em cada `docs/rf/RFnn-*.md`. Aqui ficam as três que cruzam módulos e que o
domínio implementa como enums com transições explícitas (transição inválida = exceção).

## Conta

```mermaid
stateDiagram-v2
    [*] --> PENDING_VERIFICATION: cadastro (RF01)
    PENDING_VERIFICATION --> ACTIVE: e-mail confirmado
    PENDING_VERIFICATION --> ANONYMOUS_LGPD_DELETED: 30 dias sem confirmar (limpeza)
    ACTIVE --> SUSPENDED: moderação / CRM irregular (RF02)
    SUSPENDED --> ACTIVE: recurso aceito / regularizado
    ACTIVE --> PENDING_DELETION: pedido com sudo (RF10)
    PENDING_DELETION --> ACTIVE: reativar em até 30 dias
    PENDING_DELETION --> ANONYMOUS_LGPD_DELETED: saga de eliminação
    ANONYMOUS_LGPD_DELETED --> [*]
```

| De → Para | Quem dispara | Efeitos |
|---|---|---|
| ACTIVE → SUSPENDED | moderador, job de reverificação | encerra sessões, oculta perfil, bloqueia escrita |
| ACTIVE → PENDING_DELETION | titular (sudo) | agenda saga, mantém login só para reativar |
| PENDING_DELETION → ANONYMOUS_LGPD_DELETED | `ErasureJob` | crypto-shredding da DEK social; PEP preservado |

## Selo profissional

```mermaid
stateDiagram-v2
    [*] --> NONE
    NONE --> PENDING: envia CRM/UF (+RQE)
    PENDING --> VERIFIED: fonte confere
    PENDING --> REJECTED: divergência
    PENDING --> MANUAL_REVIEW: 24 h sem resposta
    MANUAL_REVIEW --> VERIFIED
    MANUAL_REVIEW --> REJECTED
    REJECTED --> PENDING: reenvio
    VERIFIED --> PENDING: muda nome, CRM ou especialidade
    VERIFIED --> REVOKED: reverificação diária falha
    REVOKED --> PENDING: regularizado
```

Capacidades por estado (RF12): só `VERIFIED` publica, conversa, chama, registra SOAP, prescreve e aparece
no diretório.

## Documento clínico (nota, receita, pedido)

```mermaid
stateDiagram-v2
    [*] --> Rascunho
    Rascunho --> Revisao
    Revisao --> Rascunho: editar
    Revisao --> Assinando: confirmar (sudo)
    Assinando --> Assinada
    Assinando --> FalhaAssinatura
    FalhaAssinatura --> Revisao
    Assinada --> Retificada: nova versão com motivo (nota)
    Assinada --> Enviada: entregue ao paciente (receita/pedido)
    Assinada --> Cancelada: cancelar com motivo
    Enviada --> Dispensada: farmácia validou
    Enviada --> Cancelada
    Enviada --> Expirada
    Assinada --> Arquivada: 20 anos
    Retificada --> Arquivada: 20 anos
```

Invariante: a partir de **Assinada**, nenhum estado apaga o artefato assinado; só acrescenta versão ou
marca cancelamento.
