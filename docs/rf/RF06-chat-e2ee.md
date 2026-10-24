---
rf: RF06
titulo: Chat criptografado ponta a ponta
status: especificado
cards: [US12, US13]
paths:
  - apps/core-api/src/main/java/**/messaging/**
  - packages/crypto/**
---

# RF06 · Chat criptografado ponta a ponta

## Critérios de aceite

1. Conversas 1:1 e em grupo (até 50 membros) usam **MLS, RFC 9420** (ADR-010). O servidor guarda só
   mensagens cifradas e o *Delivery Service*. Ele **não** tem como decifrar.
2. Só médicos `VERIFIED` (ou profissionais verificados, conforme a matriz) iniciam conversa.
3. Cada aparelho tem seu próprio par de chaves. Adicionar um aparelho exige aprovação num aparelho já
   confiável ou a frase de recuperação.
4. A interface mostra "Criptografada ponta a ponta" e o código de segurança para comparar com o contato.
5. Anexos: cifrados no cliente com chave por arquivo e enviados ao S3. A chave segue dentro da mensagem
   MLS.
6. Denúncia: o denunciante encaminha as mensagens decifradas ao moderador. Isso fica explícito na interface.
7. Mensagens temporárias são opcionais (24 h / 7 dias / 90 dias). Padrão: **90 dias** **⚖️ validar**.
8. Conversa sobre paciente identificado: o app sugere "Registrar no prontuário" (RF08) e lembra que o chat
   não é prontuário.
9. Push só com "Nova mensagem", nunca com conteúdo.

## Máquina de estados (conversa / grupo MLS)

```mermaid
stateDiagram-v2
    [*] --> Criando: KeyPackages dos membros
    Criando --> Ativa: Welcome entregue
    Ativa --> Ativa: Commit (add / remove / update)
    Ativa --> Reequilibrando: membro perde selo ou sai
    Reequilibrando --> Ativa: Commit remove + nova epoch
    Ativa --> Arquivada: todos saem / arquivamento
    Arquivada --> [*]
```

## Classes

```mermaid
classDiagram
    class Conversation {
      +UUID id
      +ConversationType type
      +String mlsGroupId
      +long epoch
      +RetentionPolicy retention
    }
    class Device {
      +UUID id
      +UUID userId
      +byte[] identityPublicKey
      +Instant approvedAt
    }
    class KeyPackage {
      +UUID deviceId
      +byte[] payload
      +boolean consumed
    }
    class CipherMessage {
      +UUID id
      +UUID conversationId
      +long epoch
      +byte[] ciphertext
      +Instant expiresAt
    }
    class DeliveryService {
      +publishKeyPackages()
      +fanOut(CipherMessage)
      +commit(conversationId, commit)
    }
    class MlsClient {
      <<cliente>>
      +encrypt(plain) bytes
      +decrypt(bytes) plain
      +processCommit()
    }
    Conversation "1" *-- "*" CipherMessage
    Device "1" *-- "*" KeyPackage
    DeliveryService ..> Conversation
    DeliveryService ..> KeyPackage
    MlsClient ..> DeliveryService
```

## Sequência: primeira mensagem 1:1

```mermaid
sequenceDiagram
    autonumber
    actor A as Dra. A (aparelho)
    participant DS as core-api · DeliveryService
    participant P as PostgreSQL / Cassandra
    actor B as Dr. B (aparelho)
    A->>DS: GET /mls/keypackages?user=B
    DS->>P: consome um KeyPackage de cada aparelho de B
    DS-->>A: KeyPackages
    A->>A: cria grupo MLS, Commit(add B) + Welcome
    A->>DS: POST commit + welcome + mensagem cifrada
    DS->>P: grava (só cifrado)
    DS-->>B: push "Nova mensagem" (sem conteúdo)
    B->>DS: busca Welcome + mensagem
    B->>B: entra no grupo, decifra
```

## Componentes

```mermaid
flowchart LR
    subgraph Cliente
      UI[Tela de conversa] --> MC[MlsClient · OpenMLS via WASM / nativo]
      MC --> KS[Cofre de chaves do aparelho<br/>Keychain · Keystore · IndexedDB+WebCrypto]
    end
    MC -- WebSocket TLS 1.3 --> DS[DeliveryService]
    DS --> AS[Authentication Service<br/>vincula aparelho ↔ selo]
    DS --> CS[(Cassandra · mensagens cifradas com TTL)]
    DS --> PG[(PostgreSQL · grupos, aparelhos)]
    DS --> PUSH[Push sem conteúdo]
    UI -- anexo cifrado --> S3[(S3)]
```

## Atividades: enviar mensagem

```mermaid
flowchart TD
    A([Digitar mensagem]) --> B{Remetente e destinatário verificados?}
    B -- não --> X[Bloqueia com motivo]
    B -- sim --> C{Grupo MLS existe?}
    C -- não --> D[Busca KeyPackages, cria grupo, Welcome] --> E
    C -- sim --> E[Cifra na epoch atual]
    E --> F{Tem anexo?}
    F -- sim --> G[Cifra arquivo no cliente, sobe ao S3, chave vai na mensagem] --> H
    F -- não --> H[Envia ao DeliveryService]
    H --> I[Fan-out para aparelhos + push sem conteúdo]
    I --> J{Mensagem cita paciente identificável?}
    J -- sim, heurística local --> K[Sugere Registrar no prontuário · RF08]
    J -- não --> L([Fim])
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Cripto | `packages/crypto` (OpenMLS) | ⏳ |
| Serviço | `DeliveryService` | ⏳ |
| Dados | Cassandra `cipher_message` (TTL) | ⏳ |
| Testes | interop MLS entre web e mobile; teste de que o servidor não guarda texto claro | ⏳ |
| Segurança | revisão criptográfica externa | ⏳ |
