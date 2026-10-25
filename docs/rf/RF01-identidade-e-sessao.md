---
rf: RF01
titulo: Cadastro, login, 2FA e sessões
status: especificado
cards: [US01, US02, US03, US04]
paths:
  - apps/core-api/src/main/java/**/identity/**
  - apps/core-api/src/main/resources/db/migration/V1__*.sql
  - apps/core-api/src/main/resources/db/migration/V6__*.sql
  - apps/web/app/(app)/aparencia/**
  - apps/web/lib/appearance.ts
  - apps/web/app/(auth)/**
  - apps/web/app/(app)/seguranca/**
  - apps/web/lib/api.ts
  - apps/web/lib/server-api.ts
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
9. **Aparência** (preferências da conta, sincronizam entre aparelhos): tema (sistema/claro/escuro), tamanho do
   texto (85–150 %) e arte de fundo entre cinco opções, com intensidade Forte/Média/Suave (véu de 20 a 80 %).
   O texto nunca fica direto sobre a arte: o conteúdo fica num painel de leitura a 92 % da cor de superfície,
   e `scripts/background_contrast.py` garante 4,5:1 para todos os tokens de texto com a arte mais forte.

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
| Domínio | `UserAccount` (inclui `rename`, usado pelo RF03), `Credential`, `EmailVerification`, `TermsAcceptance`, `UserKey`, `UserPreference`, `LoginAudit` (JPA, `ddl-auto: validate`) | ✅ |
| Segurança | `SecurityConfig` (CSRF SPA, CSP, Referrer-Policy, HSTS, troca de id de sessão, 401 sem sessão), `AbsoluteSessionTimeoutFilter` (24 h), sessão opaca no Valkey (`indexed`), cookie `__Host-de_session` | ✅ |
| Segredos | `AppSecretProperties` (`DE_APP_SECRET` obrigatório fora de dev/it, HKDF por finalidade), `AesGcm`, `UserKeyService` (DEK por usuário) | ✅ |
| Erros | `ApiExceptionHandler` (Problem Details pt-BR, `422` com `errors[]`, `429` com `Retry-After`) | ✅ |
| Serviço | `RegistrationService` (todos os erros juntos, código 30 min / 5 tentativas / reenvio 1 min), `LoginService` (mensagem neutra, Argon2 sempre executado), `PasswordPolicy` (12–128, lista comum, sem e-mail/nome), `LoginRateLimiter` (Valkey, conta e IP) | ✅ |
| Serviço | `Totp` (RFC 6238, janela ±1, vetores do RFC no teste), `MfaService` (segredo cifrado com subchave "mfa", 10 códigos de recuperação de uso único, 5 falhas/5 min), modo sudo de 10 min em `SessionSupport` | ✅ |
| API | `AuthController` (`/csrf`, `/register`, `/login`, `/login/2fa`, `/reactivate`, `/logout`, `/session`), `GET /auth/session` com `roles` (RF12); `MeController` (`/me`, `/me/verify-email` reautentica para o principal refletir ACTIVE, `/me/verify-email/resend`) | ✅ |
| Sessão | `SessionSupport.authenticate` carrega papéis globais (`user_role`) como `ROLE_*` e a flag `de.session.mfaEnabled` usada pelas rotas que exigem 2FA (RF12) | ✅ |
| API | `MeSecurityController`: `POST /me/sudo`, `POST /me/password` (sudo, derruba as outras sessões), `GET /me/mfa`, `POST /me/mfa/totp/setup|confirm`, `POST /me/mfa/recovery-codes`, `DELETE /me/mfa`, `GET/DELETE /me/sessions[/{handle}]` (apelido por hash, nunca o id real), `POST /me/deletion` (sudo; RF10) | ✅ |
| Web | `apps/web` (Next.js 16): `/login` em etapas (senha → 2FA → reativação, 429 com Retry-After), `/cadastro` (caixas desmarcadas, 422 por campo, versão dos textos), `/verificar-email` (código, reenvio), `/seguranca` (sudo 10 min, troca de senha, TOTP + códigos de recuperação, sessões por aparelho, pedir exclusão). `lib/api.ts`: mesma origem via rewrite, CSRF SPA (`XSRF-TOKEN` → `X-XSRF-TOKEN`), Problem Details, `api.file` para PDF com o mesmo CSRF e os mesmos erros, `api.patch`; `lib/server-api.ts`: guarda de sessão no servidor (`(app)/layout.tsx`). Nunca token no cliente | ✅ |
| Testes | `SchemaConsistencyTest`, `AccountStatusTest`, `EmailVerificationTest`, `PasswordPolicyTest`, `AppSecretPropertiesTest`, `SecurityHeadersTest` | ✅ |
| Testes | `AuthFlowIT` com `support/ApiClient` (CSRF + sessão real no Valkey): cadastro → código → ativação → logout → login, 422 agrupado, CSRF, limite por conta, reenvio | ✅ |
| Migração | `V6__aparencia.sql`: `background` (none + 5 artes) e `background_veil` (20–80) em `user_preference`, com `check` | ✅ |
| API | `PreferencesController`: `GET/PUT /api/v1/me/preferences` (só os campos enviados mudam; 422 com todos os erros; linha criada com os padrões na primeira leitura) | ✅ |
| Web | `/aparencia` (miniaturas, intensidade, tema, tamanho do texto, desfazer), `lib/appearance.ts` (aplica no `<html>` e guarda cópia local só para não piscar), `AppearanceSync` no layout (servidor é a fonte da verdade), camadas arte → véu → painel em `globals.css`; artes em `design/brand/backgrounds` copiadas por `build_thumbs.py` | ✅ |
| Testes | `PreferencesIT` (padrões, atualização parcial, 422 agrupado, entra em "seus dados"), `apps/web/tests/appearance.test.ts` (aplicação no `<html>`, script de boot), e2e com as 5 artes, intensidade, painel de leitura e sincronização com outro aparelho, `background_contrast.py` no CI | ✅ |
| Testes | `apps/web/e2e/flow.cjs` (navegador real contra a API real, job `web-e2e`): cadastro → código → selo → grupo → caso → feed → LGPD → sudo → logout → login neutro; `apps/web/tests` (cliente da API, eixos do feed) | ✅ |
| Auditoria | `LoginService` e `MfaService` gravam `auth.login` (com o desfecho), `auth.logout`, `auth.password_change`, `auth.mfa_enable/disable/recovery_regenerate` na trilha encadeada do RF13, além do `login_audit` | ✅ |
| Testes | `TotpTest`, `MfaIT` (sudo obrigatório, ativar TOTP, login em etapas, código de recuperação de uso único, bloqueio após 5 erros, troca de senha derruba outras sessões, listagem sem ids reais) | ✅ |
