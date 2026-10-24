---
rf: RF12
titulo: Controle de acesso por papel e selo (RBAC hierárquico)
status: especificado
cards: [US25]
paths:
  - apps/core-api/src/main/java/**/access/**
  - apps/core-api/src/main/java/**/config/SecurityConfig.java
  - apps/clinical-api/src/main/java/**/access/**
---

# RF12 · Controle de acesso por papel e selo (RBAC hierárquico)

## Critérios de aceite

1. Regras centralizadas no `SecurityFilterChain` (rota × papel global) e checagens de domínio no serviço
   (dono do recurso → `404`; papel ou selo insuficiente → `403` com motivo em pt-BR).
2. **Papéis globais:** `PROFESSIONAL`, `MODERATOR`, `ADMIN`, `DPO`, `TECHNICAL_DIRECTOR`.
3. **Papéis institucionais** (por `Membership`): `NETWORK_ADMIN`, `HOSPITAL_ADMIN`, `CLINICAL_DIRECTOR`,
   `UNIT_MANAGER`, `STAFF_PHYSICIAN`, `AUDITOR`. Permissão **herda para baixo** na hierarquia
   rede → hospital → unidade, nunca para cima.
4. **Selo** entra como atributo (ABAC): `VERIFIED` é pré-requisito para publicar, conversar, chamar,
   prescrever e aparecer no diretório.
5. Moderador, Admin, DPO e papéis institucionais de gestão exigem **2FA**.
6. A **matriz de acesso** abaixo é a mesma que a implementação aplica e é coberta por `AccessMatrixIT`,
   que lê a tabela deste arquivo.
7. Toda decisão negada relevante gera `AuditEvent(access.denied)` sem dado sensível.

## Matriz de acesso

| Rota / ação | Visitante | Pendente | Ativo sem selo | Médico VERIFIED | Gestor (inst.) | Moderador | DPO |
|---|---|---|---|---|---|---|---|
| `GET /institutions` (diretório) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /profiles/{id}` | só PUBLIC_MINIMAL | só PUBLIC_MINIMAL | rede verificada ❌ | ✅ | ✅ | ✅ | ✅ |
| `POST /credentials` | ❌ | ❌ | ✅ | ✅ | ✅ | — | — |
| `GET /groups/**` | ❌ | ❌ | ❌ | ✅ (membro) | ❌ | ✅ | ❌ |
| `POST /posts`, comentar | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Chat / vídeo | ❌ | ❌ | ❌ | ✅ | ✅ (com selo) | ❌ | ❌ |
| `POST /clinical/**` (SOAP, Rx) | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `GET /institutions/{id}/members` (gestão) | ❌ | ❌ | ❌ | ❌ | ✅ (sua árvore) | ❌ | ❌ |
| `PUT /institutions/{id}/roles` | ❌ | ❌ | ❌ | ❌ | ✅ HOSPITAL_ADMIN+ | ❌ | ❌ |
| `GET /admin/audit` | ❌ | ❌ | ❌ | ❌ | ✅ AUDITOR (sua árvore) | ✅ | ✅ |
| `POST /admin/moderation/**` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `GET /privacy/dpo/tickets` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `/me/**` | ❌ | ✅ (parcial) | ✅ | ✅ | ✅ | ✅ | ✅ |

Legenda: ✅ permitido · ❌ negado (`401` sem sessão, `403` sem papel/selo, `404` recurso de outro dono) ·
— não se aplica (conta interna não faz esse fluxo). `AccessMatrixIT` lê esta tabela e exercita as linhas
cujas rotas já existem; "gestor" entra com o RF11.

## Máquina de estados (decisão de acesso)

```mermaid
stateDiagram-v2
    [*] --> Autenticado?
    Autenticado? --> Negado401: não
    Autenticado? --> PapelGlobal: sim
    PapelGlobal --> Negado403: rota exige papel ausente
    PapelGlobal --> Exige2FA?: papel ok
    Exige2FA? --> Negado403_2FA: papel sensível sem 2FA
    Exige2FA? --> Selo: ok
    Selo --> Negado403_Selo: ação clínica/social sem VERIFIED
    Selo --> Escopo: ok
    Escopo --> Negado404: recurso de outro dono / fora da árvore
    Escopo --> Permitido
    Permitido --> [*]
```

## Classes

```mermaid
classDiagram
    class GlobalRole {
      <<enumeration>>
      PROFESSIONAL
      MODERATOR
      ADMIN
      DPO
      TECHNICAL_DIRECTOR
    }
    class InstitutionRole {
      <<enumeration>>
      NETWORK_ADMIN
      HOSPITAL_ADMIN
      CLINICAL_DIRECTOR
      UNIT_MANAGER
      STAFF_PHYSICIAN
      AUDITOR
    }
    class Permission {
      <<enumeration>>
      MANAGE_MEMBERS
      ASSIGN_ROLES
      VIEW_AUDIT
      PUBLISH_PROTOCOL
      VIEW_DASHBOARD
    }
    class AccessContext {
      +UUID userId
      +Set~GlobalRole~ roles
      +SealStatus seal
      +boolean mfaEnabled
      +List~Membership~ memberships
    }
    class AccessPolicy {
      <<interface>>
      +decide(AccessContext, Resource, Action) Decision
    }
    class SealPolicy
    class InstitutionTreePolicy {
      +isAncestorOrSelf(instA, instB)
    }
    class MfaRequiredPolicy
    class AccessService {
      +check(ctx, resource, action)
      +requireOwnerOr404()
    }
    class Decision {
      +boolean allowed
      +int httpStatus
      +String reasonPtBr
    }
    AccessService o-- AccessPolicy
    SealPolicy ..|> AccessPolicy
    InstitutionTreePolicy ..|> AccessPolicy
    MfaRequiredPolicy ..|> AccessPolicy
    AccessService ..> Decision
    AccessContext --> GlobalRole
    InstitutionRole --> Permission
```

## Sequência

```mermaid
sequenceDiagram
    autonumber
    actor G as Gestor (HOSPITAL_ADMIN do Hospital X)
    participant E as edge
    participant SF as SecurityFilterChain
    participant C as InstitutionController
    participant A as AccessService
    participant AU as Auditoria
    G->>E: PUT /institutions/{unidadeY}/roles
    E->>SF: sessão → AccessContext
    SF->>SF: rota exige papel institucional + 2FA
    SF->>C: ok
    C->>A: check(ctx, unidadeY, ASSIGN_ROLES)
    A->>A: InstitutionTreePolicy: X é ancestral de Y?
    alt sim
      A-->>C: Permitido
      C-->>G: 200
    else não
      A->>AU: AuditEvent(access.denied)
      A-->>C: 404 (não revela a unidade)
      C-->>G: 404
    end
```

## Componentes

```mermaid
flowchart LR
    REQ[Requisição] --> SF[SecurityFilterChain<br/>rota × papel global · 2FA]
    SF --> CTRL[Controllers]
    CTRL --> AS[AccessService]
    AS --> SP[SealPolicy]
    AS --> TP[InstitutionTreePolicy]
    AS --> MP[MfaRequiredPolicy]
    AS --> PG[(PostgreSQL · memberships · roles)]
    AS --> AU[Auditoria]
    DOC[docs/rf/RF12 · matriz] -. lida por .-> IT[AccessMatrixIT]
    IT --> SF & AS
```

## Atividades: atribuir papel institucional

```mermaid
flowchart TD
    A([Gestor abre Gestão de acesso]) --> B{2FA ativo?}
    B -- não --> C[Exige ativar 2FA · RF01] --> A
    B -- sim --> D[Escolhe membro e unidade]
    D --> E{Unidade está na árvore do gestor?}
    E -- não --> F[404]
    E -- sim --> G{Papel pedido ≤ papel do gestor?}
    G -- não --> H[403: não pode conceder papel acima do seu]
    G -- sim --> I[Grava papel · AuditEvent]
    I --> J([Fim])
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Migração | `V2` (`user_role`: MODERATOR, ADMIN, DPO, TECHNICAL_DIRECTOR) | ✅ |
| Config | `SecurityConfig`: rota × papel global (`/admin/audit` → MODERATOR/ADMIN/DPO **+ 2FA**, demais `/admin/**` → MODERATOR/ADMIN **+ 2FA**, `/privacy/dpo/**` → DPO **+ 2FA** via `MfaRequiredAuthorizationManager`; rotas públicas de auth/diretório; resto autenticado), 401 sem sessão, 403 sem papel | ✅ |
| Domínio | `AccessService` (`requireActive`, `requireVerifiedSeal` para escrita social/clínica, `requireVerifiedSealOrModerator` para leitura de grupos/feed, `requireOwnerOr404`) | ✅ |
| Domínio | `InstitutionTreePolicy` (papéis institucionais, herança rede → hospital → unidade) | ⏳ RF11 |
| Testes | `SecurityHeadersTest` (401 sem sessão) | ✅ |
| Testes | `AccessMatrixIT` (lê a tabela deste doc; rotas implementadas `/me/**`, `POST /credentials`, `GET /groups/**`, `POST /posts`, `GET /admin/audit`, `GET /privacy/dpo/tickets` × visitante/pendente/ativo/verificado/moderador/DPO) | ✅ |
| Testes | `InstitutionTreePolicyTest` | ⏳ RF11 |
