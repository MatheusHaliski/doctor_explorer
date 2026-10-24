---
rf: RF03
titulo: Perfil profissional verificado
status: especificado
cards: [US07]
paths:
  - apps/core-api/src/main/java/**/profile/**
  - apps/web/app/(app)/perfil/**
---

# RF03 · Perfil profissional verificado

## Critérios de aceite

1. O perfil mostra nome, foto, CRM/UF, especialidades com RQE, instituições vinculadas e o selo.
2. Visibilidade padrão é **"só a rede verificada"** (Privacy by Default). As opções são: rede verificada,
   minha instituição, ou público (só nome, CRM e especialidade).
3. O conteúdo segue a Res. CFM 2.336/2023 (publicidade médica): nada de promessa de resultado, foto de
   antes e depois de paciente nem preço como chamariz. O texto livre passa por verificação e o moderador
   pode ocultar.
4. A foto é enviada por URL pré-assinada e limpa no servidor (sem EXIF/GPS, limite de pixels).
5. Mudar nome ou especialidade dispara RF02 (o selo volta para `PENDING`).

## Máquina de estados (visibilidade e moderação do perfil)

```mermaid
stateDiagram-v2
    [*] --> Rascunho
    Rascunho --> Publicado: selo VERIFIED
    Publicado --> EmRevisao: denúncia / filtro de publicidade
    EmRevisao --> Publicado: moderador mantém
    EmRevisao --> Oculto: moderador oculta (motivo)
    Oculto --> EmRevisao: médico edita e reenvia
    Publicado --> Rascunho: selo volta a PENDING
```

## Classes

```mermaid
classDiagram
    class ProfessionalProfile {
      +UUID id
      +String displayName
      +String bio
      +MediaRef photo
      +Visibility visibility
      +ProfileState state
      +update(ProfileEdit)
    }
    class Visibility {
      <<enumeration>>
      VERIFIED_NETWORK
      MY_INSTITUTION
      PUBLIC_MINIMAL
    }
    class AdvertisingRuleChecker {
      +check(text) List~Finding~
    }
    class ProfileService {
      +edit(id, ProfileEdit)
      +view(viewer, id) ProfileView
    }
    class ProfileView {
      +String name
      +String crm
      +List~String~ specialties
      +SealStatus seal
    }
    ProfileService ..> ProfessionalProfile
    ProfileService ..> AdvertisingRuleChecker
    ProfileService ..> ProfileView
    ProfessionalProfile --> Visibility
```

## Sequência: visualizar perfil

```mermaid
sequenceDiagram
    autonumber
    actor V as Visitante ou médico
    participant A as core-api · ProfileService
    participant P as PostgreSQL
    V->>A: GET /profiles/{id}
    A->>P: carrega perfil + selo + vínculos
    A->>A: aplica visibilidade conforme quem vê
    alt sem permissão
      A-->>V: 404 (não revela existência)
    else permitido
      A-->>V: ProfileView (campos filtrados)
    end
```

## Componentes

```mermaid
flowchart LR
    UI[Web: Perfil / Editar perfil] --> PC[ProfileController]
    PC --> PS[ProfileService]
    PS --> ARC[AdvertisingRuleChecker]
    PS --> CS[CredentialingService · RF02]
    PS --> MED[MediaService · URL pré-assinada]
    MED --> S3[(S3)]
    PS --> PG[(PostgreSQL)]
```

## Atividades: editar perfil

```mermaid
flowchart TD
    A([Editar]) --> B{Mudou nome, CRM ou especialidade?}
    B -- sim --> C[Selo → PENDING · RF02]
    B -- não --> D[Verifica regras de publicidade]
    C --> D
    D --> E{Achou problema?}
    E -- sim --> F[Mostra trecho + regra + sugestão] --> A
    E -- não --> G[Salva e publica conforme visibilidade]
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Domínio | `ProfessionalProfile` (bio, `Visibility`, `edit`, `onIdentityChanged`), `UserAccount.rename` | ✅ |
| Serviço | `AdvertisingRuleChecker` (promessa de resultado, antes e depois, superlativos, preço/promoção, sensacionalismo) | ✅ (moderação de casos limítrofes ⏳) |
| API | `GET /profiles/{id}` (visitante só vê PUBLIC_MINIMAL; rede verificada; MY_INSTITUTION exige vínculo em comum; sem acesso = 404), `PUT /me/profile` (nome, bio, visibilidade; mudar nome devolve o selo para PENDING) | ✅ |
| Mídia | foto limpa por URL pré-assinada | ⏳ RF05 |
| Web | `apps/web/app/(app)/perfil`: meu perfil (nome, apresentação com aviso de publicidade médica, visibilidade) + envio de CRM/UF e RQE e evidências do selo (RF02); `/perfil/[id]`: perfil de colega renderizado no servidor com a visibilidade decidida pela API (404 sem acesso) | ✅ |
| Testes | `DirectoryIT.profileVisibilityAndAdvertisingRules` | ✅ |
