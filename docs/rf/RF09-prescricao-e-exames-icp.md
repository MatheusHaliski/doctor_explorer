---
rf: RF09
titulo: Prescrição eletrônica e solicitação de exames com assinatura ICP-Brasil
status: especificado
cards: [US19, US20, US21]
paths:
  - apps/clinical-api/src/main/java/**/documents/**
  - apps/clinical-api/src/main/java/**/signature/**
---

# RF09 · Prescrição eletrônica e solicitação de exames com assinatura ICP-Brasil

## Critérios de aceite

1. Receita simples e pedido de exame no MVP. **Receita de controle especial** (Portaria 344/1998) e
   atestado ficam fora **⚖️**.
2. Medicamento escolhido da base **CMED/Anvisa** (princípio ativo, apresentação); posologia estruturada
   (dose, via, frequência, duração) e convertida para FHIR `Dosage`. Exame escolhido por **LOINC** com
   mapeamento TUSS.
3. Confirmação com resumo antes de assinar (heurística 5). Assinatura **PAdES** com certificado
   **ICP-Brasil** do médico via provedor de assinatura em nuvem (ADR-007), em sudo.
4. O PDF/A segue o padrão do CFM: identificação do médico (CRM/UF), do paciente, data, QR code e URL
   para validação pela farmácia (validador público, sem login, que mostra só o necessário).
5. Entrega ao paciente por link de uso único (SMS/e-mail) com validade de 7 dias, ou PDF baixado pelo
   médico. O documento fica no PEP (RF08) por 20 anos.
6. Documento assinado não se apaga: **cancela** com motivo; o validador mostra "cancelada".
7. Feedback por etapa: rascunho → assinando no provedor → assinada → enviada (heurística 1).
8. Modelos de receita por médico (heurística 7). Alertas de dose e alergia **fora do MVP** por risco de
   enquadramento SaMD **⚖️**.

## Base legal

Res. CFM 2.299/2021 (prescrição eletrônica) · Lei 14.063/2020 (assinatura eletrônica) · MP 2.200-2/2001
(ICP-Brasil) · Portaria 344/1998 · Res. CFM 1.821/2007 · LGPD Art. 11, II, "f".

## Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> Rascunho
    Rascunho --> Revisao: revisar resumo
    Revisao --> Rascunho: editar
    Revisao --> Assinando: confirmar (sudo)
    Assinando --> Assinada: PAdES ok
    Assinando --> FalhaAssinatura: certificado expirado / provedor fora
    FalhaAssinatura --> Revisao: tentar de novo
    Assinada --> Enviada: link entregue ao paciente
    Assinada --> Cancelada: cancelar com motivo
    Enviada --> Dispensada: farmácia validou
    Enviada --> Cancelada: cancelar com motivo
    Enviada --> Expirada: validade da receita
    Dispensada --> [*]
    Cancelada --> [*]
    Expirada --> [*]
```

## Classes

```mermaid
classDiagram
    class ClinicalDocument {
      <<abstract>>
      +UUID id
      +UUID encounterId
      +DocumentState state
      +SignedArtifact artifact
      +cancel(reason)
    }
    class MedicationRequest {
      +List~MedicationItem~ items
    }
    class MedicationItem {
      +String anvisaCode
      +String activeIngredient
      +String presentation
      +Dosage dosage
    }
    class Dosage {
      +String dose
      +Route route
      +String frequency
      +Duration duration
      +String patientInstruction
    }
    class ServiceRequest {
      +List~ExamItem~ items
      +String clinicalIndication
    }
    class ExamItem {
      +String loinc
      +String tuss
    }
    class SignedArtifact {
      +String s3Key
      +String sha256
      +String validationCode
      +Instant signedAt
      +String certSubject
    }
    class SignatureProviderPort {
      <<interface>>
      +signPades(pdf, userId) SignedArtifact
    }
    class DocumentService {
      +draft()
      +sign(id)
      +deliver(id, channel)
      +cancel(id, reason)
    }
    class PublicValidator {
      +validate(code) ValidationView
    }
    ClinicalDocument <|-- MedicationRequest
    ClinicalDocument <|-- ServiceRequest
    MedicationRequest "1" *-- "1..*" MedicationItem
    MedicationItem "1" *-- "1" Dosage
    ServiceRequest "1" *-- "1..*" ExamItem
    ClinicalDocument "1" --> "0..1" SignedArtifact
    DocumentService ..> SignatureProviderPort
    DocumentService ..> ClinicalDocument
    PublicValidator ..> SignedArtifact
```

## Sequência: emitir receita

```mermaid
sequenceDiagram
    autonumber
    actor M as Médico
    participant W as Web
    participant D as clinical-api · DocumentService
    participant R as Renderizador PDF/A
    participant ICP as Provedor ICP-Brasil (nuvem)
    participant S3 as S3 (cofre)
    participant N as Entrega (SMS/e-mail)
    actor F as Farmácia
    M->>W: monta receita (CMED + posologia) ou aplica modelo
    W->>D: POST /clinical/prescriptions (rascunho)
    M->>W: Revisar → Confirmar (sudo)
    W->>D: POST /prescriptions/{id}/sign
    D->>R: gera PDF/A + QR + código de validação
    D->>ICP: assinar PAdES (autorização do médico no provedor)
    ICP-->>M: desafio (app / PIN do certificado)
    M-->>ICP: autoriza
    ICP-->>D: PDF assinado + cadeia
    D->>S3: grava cifrado (Object Lock)
    D-->>W: "Assinada" (status por etapa)
    M->>W: Enviar ao paciente
    W->>D: POST /prescriptions/{id}/deliver
    D->>N: link de uso único, 7 dias
    F->>D: GET /validar/{codigo}
    D-->>F: válida · médico · itens · não cancelada
```

## Componentes

```mermaid
flowchart LR
    UI[Web: editor de receita e exame] --> DC[DocumentController]
    DC --> DS[DocumentService]
    DS --> MED[Catálogo CMED/Anvisa]
    DS --> LO[Catálogo LOINC ↔ TUSS]
    DS --> PDF[Renderizador PDF/A + QR]
    DS --> SP{{SignatureProviderPort}}
    SP --> ICP[Adapter provedor ICP-Brasil · ADR-007]
    DS --> S3[(S3 Object Lock)]
    DS --> PGC[(PostgreSQL clínico)]
    DS --> DL[Entrega: SMS · e-mail · link único]
    PV[/Validador público /validar/] --> DS
    DS --> AU[Auditoria]
    DS --> RNDS[RNDS · envio de dispensação, roadmap]
```

## Atividades

```mermaid
flowchart TD
    A([Nova receita]) --> B{Médico VERIFIED com certificado ICP cadastrado?}
    B -- não --> C[Orienta a cadastrar certificado] --> Z([Fim])
    B -- sim --> D[Seleciona paciente do cofre]
    D --> E[Adiciona itens: CMED + posologia estruturada]
    E --> F{Controle especial?}
    F -- sim --> G[Bloqueia: fora do MVP, com explicação] --> E
    F -- não --> H[Revisão com resumo completo]
    H --> I{Confirmar?}
    I -- não --> E
    I -- sim --> J[Sudo → PDF/A → PAdES no provedor]
    J --> K{Assinou?}
    K -- não --> L[Erro com causa e ação: renovar certificado / tentar de novo] --> H
    K -- sim --> M[Guarda no PEP · gera código de validação]
    M --> N{Enviar ao paciente?}
    N -- sim --> O[Link único 7 dias]
    N -- não --> P[Baixar PDF]
    O & P --> Z
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Domínio | `MedicationRequest`, `ServiceRequest`, `SignedArtifact` | ⏳ |
| Integração | adapter ICP-Brasil (ADR-007), catálogos CMED/LOINC | ⏳ |
| API pública | `/validar/{codigo}` | ⏳ |
| Testes | `PrescriptionSignIT` com provedor simulado; validação PAdES com biblioteca independente | ⏳ |
