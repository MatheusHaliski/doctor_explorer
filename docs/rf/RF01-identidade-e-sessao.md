---
rf: RF01
titulo: Cadastro, login, 2FA e sessões
status: especificado
cards: [US01, US02, US03, US04]
paths:
  - apps/core-api/src/main/java/**/identity/**
  - apps/core-api/src/main/resources/db/migration/V1__*.sql
  - apps/web/app/(auth)/**
---

# RF01 · Cadastro, login, 2FA e sessões

Base de identidade do Doctor Explorer: sessão opaca no Valkey, CSRF no padrão SPA, Argon2id, TOTP,
códigos de recuperação e **modo sudo**. Nunca JWT no cliente (ADR-008).

## Critérios de aceite

1. `POST /api/v1/auth/register` cria a conta em `PENDING_VERIFICATION`, abre sessão e envia código de
   6 dígitos (validade 30 min, 5 tentativas, reenvio 1/min). Devolve **todos** os erros juntos em `422`.
2. Aceite dos Termos e da Política vem com **caixa desmarcada**; versão, data e IP ficam em
   `terms_acceptance`.
3. Login em etapas: senha → `POST /login/2fa` (se tiver 2FA) → oferta de `POST /reactivate` (se
   `PENDING_DELETION`). O estado intermediário fica na sessão, nunca no cliente.
4. A mensagem de erro de login é neutra: "E-mail ou senha incorretos". Limite de tentativas por conta e
   por IP → `429` + `Retry-After`.
5. Cookie `__Host-de_session`: `HttpOnly`, `Secure`, `SameSite=Lax`, sem `Max-Age`. Ocioso de 60 min
   (deslizante) e absoluto de 24 h.
6. Trocar senha, ativar ou desativar 2FA, pedir exclusão e **assinar documento clínico** exigem modo sudo
   (senha + TOTP), que vale 10 min.
7. `GET/DELETE /me/sessions` lista aparelhos sem expor o id real da sessão.
8. Moderador, Admin e Gestor hospitalar não usam a conta sem 2FA ativo.

## Base legal

LGPD Art. 46 (segurança), Art. 6º, VII (prevenção) · ECA Digital, se houver menor (fora do MVP: só
profissionais maiores de 18).

## Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> Anonima
    Anonima --> SenhaOk: POST /login (senha válida)
    Anonima --> Anonima: senha inválida (contador++)
    Anonima --> Bloqueada: limite de tentativas
    Bloqueada --> Anonima: Retry-After expirou
    SenhaOk --> Autenticada: sem 2FA
    SenhaOk --> Aguardando2FA: com 2FA
    Aguardando2FA --> Autenticada: TOTP ou código de recuperação ok
    Aguardando2FA --> Anonima: 5 falhas / 5 min
    Autenticada --> OfertaReativacao: conta PENDING_DELETION
    OfertaReativacao --> Autenticada: POST /reactivate
    OfertaReativacao --> Anonima: recusa
    Autenticada --> Sudo: senha + TOTP
    Sudo --> Autenticada: 10 min
    Autenticada --> Anonima: logout / ocioso 60 min / absoluto 24 h / encerrada em outro aparelho
```

## Classes

```mermaid
classDiagram
    class UserAccount {
      +UUID id
      +String email
      +AccountStatus status
      +LocalDate birthDate
      +Instant createdAt
      +activate()
      +requestDeletion(Instant now)
      +reactivate()
    }
    class AccountStatus {
      <<enumeration>>
      PENDING_VERIFICATION
      ACTIVE
      SUSPENDED
      PENDING_DELETION
      ANONYMOUS_LGPD_DELETED
    }
    class Credential {
      +String passwordHash
      +Instant changedAt
    }
    class MfaSecret {
      +byte[] totpSecretCipher
      +boolean enabled
      +List~RecoveryCode~ recoveryCodes
    }
    class RecoveryCode {
      +String hash
      +Instant usedAt
    }
    class EmailVerification {
      +String codeHash
      +int attempts
      +Instant expiresAt
    }
    class TermsAcceptance {
      +String termsVersion
      +String privacyVersion
      +Instant acceptedAt
      +String ipHash
    }
    class AuthService {
      +register(RegisterRequest) UserAccount
      +login(email, password) LoginStep
      +verifyTotp(code) LoginStep
      +elevateSudo(password, code)
    }
    class PasswordPolicy {
      +validate(password, email, handle) List~Violation~
    }
    class LoginRateLimiter {
      +check(accountKey, ip)
      +recordFailure(accountKey, ip)
    }
    UserAccount --> AccountStatus
    UserAccount "1" *-- "1" Credential
    UserAccount "1" *-- "0..1" MfaSecret
    MfaSecret "1" *-- "10" RecoveryCode
    UserAccount "1" *-- "0..1" EmailVerification
    UserAccount "1" *-- "1..*" TermsAcceptance
    AuthService ..> PasswordPolicy
    AuthService ..> LoginRateLimiter
    AuthService ..> UserAccount
```

## Sequência: login com 2FA

```mermaid
sequenceDiagram
    autonumber
    actor U as Médico
    participant W as Web (Next.js)
    participant E as edge
    participant A as core-api · AuthService
    participant V as Valkey
    participant P as PostgreSQL
    W->>E: GET /api/v1/auth/csrf
    E-->>W: cookie XSRF-TOKEN
    U->>W: e-mail + senha
    W->>E: POST /login (X-XSRF-TOKEN)
    E->>A: encaminha
    A->>V: confere limite (conta, IP)
    A->>P: busca conta + hash Argon2id
    A->>A: verifica senha
    A->>V: sessão nova (id trocado), etapa = AGUARDANDO_2FA
    A-->>W: 200 {next: "2fa"}
    U->>W: código TOTP
    W->>E: POST /login/2fa
    E->>A: encaminha
    A->>P: decifra segredo (AES-256-GCM) e valida janela ±1
    A->>V: etapa = AUTENTICADA, troca id da sessão
    A-->>W: 200 {user}
```

## Sequência: ativar 2FA

```mermaid
sequenceDiagram
    autonumber
    actor U as Médico
    participant W as Web
    participant A as core-api
    participant P as PostgreSQL
    U->>W: Segurança → Ativar 2FA
    W->>A: POST /me/mfa/totp/setup (sudo: senha)
    A->>A: gera segredo, cifra com subchave "mfa"
    A->>P: grava segredo pendente
    A-->>W: otpauth:// URI + QR
    U->>W: digita código do app autenticador
    W->>A: POST /me/mfa/totp/confirm
    A->>P: enabled = true, gera 10 códigos (hash)
    A-->>W: códigos de recuperação (mostrados uma vez)
```

## Componentes

```mermaid
flowchart LR
    subgraph Web
      LoginPage[Tela Login] --> ApiClient[packages/api]
      RegisterPage[Tela Cadastro] --> ApiClient
      SecurityPage[Sessões e segurança] --> ApiClient
    end
    subgraph edge
      TLS[TLS 1.3 + WAF]
      RL[Rate limit por IP]
    end
    subgraph core-api
      SF[SecurityFilterChain<br/>CSRF SPA · CSP · sessão]
      AC[AuthController]
      MC[MeController]
      AS[AuthService]
      MS[MfaService]
      PP[PasswordPolicy]
      LR[LoginRateLimiter]
      ML[Mailer]
    end
    ApiClient --> TLS --> RL --> SF --> AC & MC
    AC --> AS --> PP & LR
    MC --> MS
    AS --> ML
    AS & MS --> PG[(PostgreSQL)]
    SF & LR --> VK[(Valkey · Spring Session)]
```

## Atividades: cadastro

```mermaid
flowchart TD
    A([Início]) --> B[Preenche nome, e-mail, nascimento, senha, CRM/UF]
    B --> C{Marcou Termos e Privacidade?}
    C -- não --> E1[422: aceite obrigatório] --> B
    C -- sim --> D{Validação completa}
    D -- erros --> E2["422 com errors[] completos"] --> B
    D -- ok --> F{Idade ≥ 18?}
    F -- não --> E3[Bloqueia: público do MVP é só profissional adulto]
    F -- sim --> G[Cria conta PENDING_VERIFICATION + DEK do usuário]
    G --> H[Registra aceite com versão, data e IP]
    H --> I[Abre sessão e envia código por e-mail]
    I --> J{Código correto em até 30 min?}
    J -- sim --> K[ACTIVE · selo NONE] --> L([Encaminha para RF02])
    J -- não --> M{Tentativas < 5?}
    M -- sim --> J
    M -- não --> N[Reenviar código · 1 por min] --> J
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Migração | `V1__identidade.sql` (contas, credencial, 2FA, verificação, termos, consentimentos, preferências, `user_key`, `login_audit`) + `schema.sql` | ✅ |
| Domínio | `AccountStatus`, `SealStatus` (máquinas de estado com `InvalidTransitionException`) | ✅ |
| Domínio | `UserAccount`, `Credential`, `EmailVerification`, `TermsAcceptance`, `UserKey`, `UserPreference`, `LoginAudit` (JPA, `ddl-auto: validate`) | ✅ |
| Segurança | `SecurityConfig` (CSRF SPA, CSP, Referrer-Policy, HSTS, troca de id de sessão, 401 sem sessão), `AbsoluteSessionTimeoutFilter` (24 h), sessão opaca no Valkey (`indexed`), cookie `__Host-de_session` | ✅ |
| Segredos | `AppSecretProperties` (`DE_APP_SECRET` obrigatório fora de dev/it, HKDF por finalidade), `AesGcm`, `UserKeyService` (DEK por usuário) | ✅ |
| Erros | `ApiExceptionHandler` (Problem Details pt-BR, `422` com `errors[]`, `429` com `Retry-After`) | ✅ |
| Serviço | `RegistrationService` (todos os erros juntos, código 30 min / 5 tentativas / reenvio 1 min), `LoginService` (mensagem neutra, Argon2 sempre executado), `PasswordPolicy` (12–128, lista comum, sem e-mail/nome), `LoginRateLimiter` (Valkey, conta e IP) | ✅ |
| Serviço | `MfaService` (TOTP, códigos de recuperação, sudo) | ⏳ passo 3 |
| API | `AuthController` (`/csrf`, `/register`, `/login`, `/reactivate`, `/logout`, `/session`), `MeController` (`/me`, `/me/verify-email`, `/me/verify-email/resend`) | ✅ |
| API | `/login/2fa`, `/me/password`, `/me/sessions`, `/me/mfa/**` | ⏳ passo 3 |
| Web | telas Login, Cadastro, Segurança (protótipo em `design/prototype`: Sessões e segurança ✅) | 🟡 |
| Testes | `SchemaConsistencyTest`, `AccountStatusTest`, `EmailVerificationTest`, `PasswordPolicyTest`, `AppSecretPropertiesTest`, `SecurityHeadersTest` | ✅ |
| Testes | `AuthFlowIT` com `support/ApiClient` (CSRF + sessão real no Valkey): cadastro → código → ativação → logout → login, 422 agrupado, CSRF, limite por conta, reenvio | ✅ |
| Testes | `MfaIT` | ⏳ passo 3 |
