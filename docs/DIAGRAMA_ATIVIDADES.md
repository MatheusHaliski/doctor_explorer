# Diagramas de atividades transversais

Por RF: ver `docs/rf/`. Aqui, os dois fluxos que o time inteiro precisa conhecer.

## Onboarding do médico (do cadastro ao primeiro ato clínico)

```mermaid
flowchart TD
    A([Chega ao Doctor Explorer]) --> B[Cadastro + Termos desmarcados · RF01]
    B --> C[Confirma e-mail]
    C --> D[Envia CRM/UF e RQE · RF02]
    D --> E{Selo}
    E -- REJECTED --> F[Corrige e reenvia] --> D
    E -- PENDING --> G[Pode editar perfil e ler ajuda, nada mais]
    G --> E
    E -- VERIFIED --> H[Ativa 2FA · recomendado]
    H --> I[Entra nos grupos da especialidade · RF04]
    I --> J{Primeiro ato clínico?}
    J -- discutir caso --> K[Publica caso anonimizado · RF05]
    J -- prescrever --> L[Cadastra certificado ICP-Brasil · RF09]
    K & L --> M([Uso pleno])
```

## Ciclo de release com avaliação heurística

```mermaid
flowchart LR
    A[Sprint termina] --> B[Build + testes + Docs por RF + CodeQL/Gitleaks/Trivy]
    B --> C{CI verde?}
    C -- não --> A
    C -- sim --> D[Avaliação heurística · 3 a 5 avaliadores]
    D --> E[Consolida problemas · severidade 0 a 4]
    E --> F{Algum 3 ou 4 aberto?}
    F -- sim --> G[Corrige · volta ao CI] --> B
    F -- não --> H[Homologação com dado sintético]
    H --> I[Aprovação: PO + Diretor-Técnico + DPO]
    I --> J([Release])
```
