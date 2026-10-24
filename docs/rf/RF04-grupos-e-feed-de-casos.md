---
rf: RF04
titulo: Grupos fechados por especialidade e feed de casos clínicos
status: especificado
cards: [US08, US09, US10]
paths:
  - apps/core-api/src/main/java/**/community/**
  - apps/web/app/(app)/grupos/**
---

# RF04 · Grupos fechados por especialidade e feed de casos clínicos

## Critérios de aceite

1. Cada especialidade tem um grupo **fechado**. Entra automaticamente quem tem o RQE correspondente
   verificado; os demais pedem entrada e o moderador do grupo aprova.
2. O post de caso tem estrutura própria: queixa, história, exame, exames complementares, pergunta. Toda
   mídia passa obrigatoriamente pelo RF05 antes de publicar.
3. Antes de publicar, o texto passa por um detector de identificadores (nome, CPF, CNS, nº de prontuário,
   telefone, data completa, endereço). Se encontrar algo, **bloqueia** e destaca o trecho.
4. O conteúdo do grupo não é indexado por mecanismo de busca nem aparece fora do grupo.
5. Comentários aceitam marcação "parecer" (opinião técnica). O botão "Registrar no prontuário" (RF08)
   aparece só para o autor do caso.
6. Denúncia, ocultação e exclusão por moderação ficam registradas na auditoria.
7. Feed ordenado por recência, com filtro por especialidade e "sem resposta". Sem algoritmo de
   engajamento no MVP.

## Base legal

CFM: sigilo profissional (CEM arts. 73 a 79) · LGPD Art. 12 (dado anonimizado deixa de ser dado pessoal,
desde que a anonimização não seja reversível com meios razoáveis) **⚖️ validar**.

## Máquina de estados (post de caso)

```mermaid
stateDiagram-v2
    [*] --> Rascunho
    Rascunho --> AguardandoAnonimizacao: anexou mídia
    AguardandoAnonimizacao --> RevisaoDoAutor: RF05 concluiu
    Rascunho --> RevisaoDoAutor: sem mídia
    RevisaoDoAutor --> Bloqueado: detector achou identificador
    Bloqueado --> RevisaoDoAutor: autor corrige
    RevisaoDoAutor --> Publicado: autor confirma "sem dado identificável"
    Publicado --> Resolvido: autor marca resolvido
    Publicado --> Oculto: moderação
    Resolvido --> Oculto: moderação
    Publicado --> Excluido: autor exclui
    Oculto --> Publicado: recurso aceito
    Excluido --> [*]
```

## Classes

```mermaid
classDiagram
    class SpecialtyGroup {
      +UUID id
      +String specialtyCode
      +String name
      +JoinPolicy joinPolicy
    }
    class GroupMembership {
      +UUID userId
      +GroupRole role
      +Instant joinedAt
    }
    class CasePost {
      +UUID id
      +UUID authorId
      +CaseSections sections
      +List~MediaAsset~ media
      +PostState state
      +publish()
      +hide(reason)
    }
    class CaseSections {
      +String complaint
      +String history
      +String exam
      +String workup
      +String question
    }
    class Comment {
      +UUID id
      +UUID authorId
      +String body
      +boolean isOpinion
    }
    class IdentifierDetector {
      +scan(text) List~Match~
    }
    class CommunityService {
      +createDraft()
      +publish(postId)
      +comment(postId, body)
    }
    SpecialtyGroup "1" *-- "*" GroupMembership
    SpecialtyGroup "1" *-- "*" CasePost
    CasePost "1" *-- "1" CaseSections
    CasePost "1" *-- "*" Comment
    CommunityService ..> IdentifierDetector
    CommunityService ..> CasePost
```

## Sequência: publicar caso

```mermaid
sequenceDiagram
    autonumber
    actor A as Médico autor
    participant W as Web
    participant C as core-api · CommunityService
    participant D as IdentifierDetector
    participant M as MediaService (RF05)
    participant P as PostgreSQL
    participant N as Notificações
    A->>W: escreve caso + anexa imagem
    W->>M: upload (URL pré-assinada)
    M-->>W: prévia anonimizada
    A->>W: Publicar
    W->>C: POST /groups/{g}/posts
    C->>D: scan(texto)
    alt achou identificador
      D-->>C: matches
      C-->>W: 422 com trechos destacados
    else limpo
      C->>P: grava post Publicado
      C->>N: avisa membros (sem conteúdo clínico no push)
      C-->>W: 201
    end
```

## Componentes

```mermaid
flowchart LR
    UI[Web: Grupos · Feed · Post] --> GC[GroupController]
    UI --> PC[PostController]
    GC & PC --> CS[CommunityService]
    CS --> ID[IdentifierDetector<br/>regex + NER pt-BR]
    CS --> MS[MediaService · RF05]
    CS --> MOD[ModerationService]
    CS --> NT[NotificationService → Cassandra]
    CS --> PG[(PostgreSQL)]
    MOD --> AU[Auditoria]
```

## Atividades

```mermaid
flowchart TD
    A([Novo caso]) --> B[Preenche seções]
    B --> C{Tem mídia?}
    C -- sim --> D[RF05: anonimizar + revisão] --> E
    C -- não --> E[Detector de identificadores no texto]
    E --> F{Encontrou?}
    F -- sim --> G[Destaca trechos] --> B
    F -- não --> H[Autor confirma: sem dado identificável]
    H --> I[Publica no grupo]
    I --> J[Pares comentam / dão parecer]
    J --> K{Autor quer registrar no PEP?}
    K -- sim --> L[RF08]
    K -- não --> M([Fim])
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Migração | `V4__community.sql` | ⏳ |
| Domínio | `SpecialtyGroup`, `CasePost`, `Comment` | ⏳ |
| Serviço | `CommunityService`, `IdentifierDetector` | ⏳ |
| Web | Grupos, Feed, Novo caso | ⏳ |
| Testes | `CasePublishIT`, `IdentifierDetectorTest` (corpus de exemplos) | ⏳ |
