---
rf: RF07
titulo: Chamadas de vídeo criptografadas ponta a ponta
status: especificado
cards: [US14, US15]
paths:
  - apps/rtc/**
  - apps/core-api/src/main/java/**/calls/**
---

# RF07 · Chamadas de vídeo criptografadas ponta a ponta

## Critérios de aceite

1. Chamada 1:1 e em grupo (até 8 participantes no MVP) entre usuários `VERIFIED`.
2. Mídia por **WebRTC com DTLS-SRTP** (obrigatório) **mais** E2EE por *insertable streams* (SFrame, ADR-006).
   O SFU encaminha pacotes sem conseguir decifrar. A chave SFrame é distribuída pelo grupo MLS da
   conversa (RF06).
3. A tela mostra "Criptografada ponta a ponta", a qualidade da rede e quem está na chamada
   (heurística 1).
4. Compartilhamento de tela permitido, com aviso de que o conteúdo pode conter dado de paciente.
5. **Sem gravação no servidor.** Gravação local é bloqueada no app; não há como impedir captura externa,
   então o aviso aparece ao entrar.
6. Chamada **não** é consulta com paciente: a teleconsulta (Res. CFM 2.314/2022) fica fora do MVP. O
   convite só aceita usuários da plataforma.
7. Conversa clínica em chamada: botão "Registrar no prontuário" (RF08) ao encerrar.
8. Sinalização por WebSocket autenticado pela sessão; TURN com credenciais efêmeras (TTL 10 min).

## Máquina de estados (chamada)

```mermaid
stateDiagram-v2
    [*] --> Convidando: iniciar
    Convidando --> Tocando: convite entregue
    Convidando --> Perdida: ninguém atende em 45 s
    Tocando --> Conectando: aceitar
    Tocando --> Recusada: recusar
    Conectando --> EmAndamento: DTLS ok + chave SFrame recebida
    Conectando --> Falhou: ICE falhou / sem chave
    EmAndamento --> EmAndamento: entra ou sai participante (nova chave)
    EmAndamento --> Reconectando: perda de rede
    Reconectando --> EmAndamento: ICE restart
    Reconectando --> Encerrada: 30 s sem rede
    EmAndamento --> Encerrada: último participante sai
    Encerrada --> [*]
    Perdida --> [*]
    Recusada --> [*]
    Falhou --> [*]
```

## Classes

```mermaid
classDiagram
    class Call {
      +UUID id
      +UUID conversationId
      +CallState state
      +Instant startedAt
      +Instant endedAt
      +List~Participant~ participants
      +join(userId, deviceId)
      +leave(userId)
    }
    class Participant {
      +UUID userId
      +UUID deviceId
      +Instant joinedAt
      +boolean screenSharing
    }
    class SignalingService {
      +offer/answer/candidate
      +issueTurnCredentials(userId) TurnCredentials
    }
    class TurnCredentials {
      +String username
      +String password
      +Instant expiresAt
    }
    class SframeKeyRing {
      <<cliente>>
      +deriveFromMlsEpoch(epoch) Key
      +rotate()
    }
    class CallMetadataLog {
      +UUID callId
      +int participants
      +Duration duration
      +String qualityBucket
    }
    Call "1" *-- "*" Participant
    SignalingService ..> Call
    SignalingService ..> TurnCredentials
    Call "1" --> "1" CallMetadataLog
```

Só metadados (duração, participantes, qualidade) são guardados. Nunca conteúdo.

## Sequência

```mermaid
sequenceDiagram
    autonumber
    actor A as Dra. A
    participant S as rtc · Signaling (WS)
    participant SFU as SFU
    participant T as TURN
    actor B as Dr. B
    A->>S: iniciar chamada (conversationId)
    S->>B: push "Chamada recebida" (sem conteúdo)
    B->>S: aceitar
    S-->>A: credenciais TURN efêmeras
    S-->>B: credenciais TURN efêmeras
    A->>SFU: offer (DTLS-SRTP)
    B->>SFU: offer (DTLS-SRTP)
    Note over A,B: chave SFrame derivada da epoch MLS da conversa (RF06)
    A->>SFU: mídia cifrada SFrame dentro do SRTP
    SFU->>B: encaminha sem decifrar
    B->>SFU: mídia cifrada
    SFU->>A: encaminha
    A->>S: encerrar
    S->>S: grava só metadados
```

## Componentes

```mermaid
flowchart LR
    subgraph Cliente
      UI[Tela de chamada] --> RTC[WebRTC + insertable streams]
      RTC --> KR[SframeKeyRing ← MLS epoch]
    end
    RTC -- WSS --> SIG[Signaling · apps/rtc]
    RTC -- DTLS-SRTP --> SFU[SFU · mediasoup ou LiveKit · ADR-006]
    RTC -- relay --> TURN[coturn · credenciais efêmeras]
    SIG --> SESS[(Valkey · sessão)]
    SIG --> META[(PostgreSQL · metadados da chamada)]
    SIG --> AU[Auditoria]
```

## Atividades

```mermaid
flowchart TD
    A([Iniciar chamada]) --> B{Todos verificados e na mesma conversa?}
    B -- não --> X[Bloqueia com motivo]
    B -- sim --> C[Convida · push sem conteúdo]
    C --> D{Alguém aceitou em 45 s?}
    D -- não --> E[Perdida · registra metadado]
    D -- sim --> F[Negocia ICE/DTLS + chave SFrame]
    F --> G{Conectou?}
    G -- não --> H[Mostra erro com causa e sugestão: trocar de rede]
    G -- sim --> I[Chamada em andamento · indicador E2EE]
    I --> J{Compartilhar tela?}
    J -- sim --> K[Aviso de dado de paciente] --> I
    J -- não --> I
    I --> L[Encerrar]
    L --> M{Registrar no prontuário?}
    M -- sim --> N[RF08]
    M -- não --> O([Fim · só metadados guardados])
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Serviço | `apps/rtc` (signaling + SFU) | ⏳ |
| Cliente | `packages/rtc-client` (SFrame) | ⏳ |
| Infra | coturn, SFU em 3 AZs | ⏳ |
| Testes | 2 navegadores headless em chamada; verificação de que o SFU não decifra | ⏳ |
