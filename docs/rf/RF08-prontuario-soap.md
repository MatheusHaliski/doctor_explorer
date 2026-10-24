---
rf: RF08
titulo: Prontuário eletrônico simplificado (SOAP) vinculado à discussão
status: especificado
cards: [US16, US17, US18]
paths:
  - apps/clinical-api/src/main/java/**/ehr/**
  - apps/clinical-api/src/main/java/**/fhir/**
---

# RF08 · Prontuário eletrônico simplificado (SOAP) vinculado à discussão

O PEP vive na **`clinical-api`**, com banco e chaves separados (ADR-001). Modelo interno **openEHR**
(Composition por encontro) e fachada **FHIR R4** (ADR-003, ADR-004).

## Critérios de aceite

1. Só médico `VERIFIED` cria nota. O paciente é identificado no **cofre clínico** (do médico ou da
   instituição), nunca no fórum.
2. A nota tem os quatro campos SOAP: Subjetivo, Objetivo, Avaliação (com CID-10 e, opcionalmente,
   SNOMED CT) e Plano. Rascunho salvo a cada 5 s.
3. Pode vincular um post de caso (RF04), uma conversa (RF06) ou uma chamada (RF07) pelo **id opaco**. O
   fórum nunca recebe o id do paciente.
4. Assinatura: no MVP, assinatura eletrônica avançada (sessão + sudo + carimbo de tempo do servidor).
   ICP-Brasil é obrigatório para receita e exame (RF09); para a nota é **⚖️ validar** com o Diretor-Técnico
   (Res. CFM 1.821/2007 e SBIS NGS2 exigem ICP-Brasil para eliminar o papel).
5. Nota assinada é **imutável**. Correção gera nova versão com motivo; as duas ficam visíveis.
6. Guarda mínima de **20 anos** desde o último registro; a chave do cofre não é destruída pela exclusão
   da conta social.
7. Toda leitura e escrita gera `AuditEvent` (quem, quando, qual paciente, por quê).
8. `/fhir/r4/Composition`, `Encounter`, `Patient`, `Practitioner`, `Condition`, `Provenance` expostos e
   validados contra os perfis no CI.

## Base legal

Res. CFM 1.638/2002 (prontuário), 1.821/2007 (digitalização e guarda), Lei 13.787/2018 (20 anos) ·
LGPD Art. 11, II, "f" (tutela da saúde) e Art. 16, I (conservação por obrigação legal).

## Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> Rascunho: nova nota
    Rascunho --> Rascunho: autosave
    Rascunho --> Assinada: assinar (sudo)
    Rascunho --> Descartada: descartar
    Assinada --> Retificada: nova versão com motivo
    Retificada --> Retificada: nova versão
    Assinada --> Arquivada: 20 anos sem acesso
    Retificada --> Arquivada
    Arquivada --> [*]
    Descartada --> [*]
```

## Classes

```mermaid
classDiagram
    class ClinicalVault {
      +UUID id
      +VaultOwnerType ownerType
      +UUID ownerId
      +UUID keyId
    }
    class Patient {
      +UUID id
      +EncryptedField name
      +EncryptedField cpf
      +EncryptedField birthDate
      +String localRecordNumber
    }
    class Encounter {
      +UUID id
      +UUID patientId
      +UUID practitionerId
      +Instant occurredAt
      +EncounterContext context
    }
    class SoapComposition {
      +UUID id
      +int version
      +EncryptedField subjective
      +EncryptedField objective
      +Assessment assessment
      +EncryptedField plan
      +CompositionState state
      +Signature signature
      +sign()
      +amend(reason) SoapComposition
    }
    class Assessment {
      +List~String~ icd10
      +List~String~ snomed
      +EncryptedField text
    }
    class DiscussionLink {
      +LinkType type
      +UUID opaqueRef
    }
    class Signature {
      +UUID signerId
      +Instant signedAt
      +String tsaToken
      +String hash
    }
    class FhirFacade {
      +toComposition(SoapComposition) Composition
      +toEncounter(Encounter) Encounter
    }
    ClinicalVault "1" *-- "*" Patient
    Patient "1" *-- "*" Encounter
    Encounter "1" *-- "1..*" SoapComposition
    SoapComposition "1" *-- "1" Assessment
    SoapComposition "1" *-- "0..*" DiscussionLink
    SoapComposition "1" --> "0..1" Signature
    FhirFacade ..> SoapComposition
```

## Sequência: registrar discussão no prontuário

```mermaid
sequenceDiagram
    autonumber
    actor M as Médico assistente
    participant W as Web
    participant C as clinical-api · EhrService
    participant K as KMS (chave do cofre)
    participant P as PostgreSQL clínico
    participant AU as Auditoria
    M->>W: no post do caso, "Registrar no prontuário"
    W->>C: GET /clinical/patients?q=... (busca no cofre)
    C->>AU: AuditEvent(patient.search)
    C-->>W: pacientes (nome parcial, nascimento)
    M->>W: escolhe paciente, preenche SOAP (rascunho a cada 5 s)
    W->>C: PUT /clinical/encounters/{e}/soap (rascunho)
    M->>W: Assinar
    W->>C: POST /soap/{id}/sign (sudo)
    C->>K: decifra DEK do cofre
    C->>P: grava versão 1 cifrada + hash + carimbo de tempo + link opaco do post
    C->>AU: AuditEvent(composition.sign)
    C-->>W: 201 "Nota assinada"
```

## Componentes

```mermaid
flowchart LR
    UI[Web: editor SOAP] --> EC[EhrController]
    UI --> FC[/fhir/r4 FhirController/]
    EC --> ES[EhrService]
    FC --> FF[FhirFacade · HAPI FHIR]
    FF --> ES
    ES --> TS[TerminologyService<br/>CID-10 · SNOMED CT · LOINC]
    ES --> VK[VaultKeyService] --> KMS[[KMS]]
    ES --> TSA[Carimbo de tempo]
    ES --> PGC[(PostgreSQL clínico · réplicas)]
    ES --> AU[Auditoria · Cassandra + S3 Object Lock]
```

## Atividades

```mermaid
flowchart TD
    A([Registrar no prontuário]) --> B{Médico VERIFIED com cofre?}
    B -- não --> X[403 com motivo]
    B -- sim --> C[Busca paciente no cofre]
    C --> D{Existe?}
    D -- não --> E[Cadastra paciente mínimo: nome, nascimento, CPF opcional]
    D -- sim --> F
    E --> F[Abre encontro + nota SOAP]
    F --> G[Preenche S · O · A com CID-10 · P]
    G --> H{Vincular discussão?}
    H -- sim --> I[Grava id opaco do post, conversa ou chamada] --> J
    H -- não --> J[Revisar resumo]
    J --> K[Assinar com sudo]
    K --> L[Versão imutável + carimbo + auditoria]
    L --> M([Fim])
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Migração | `clinical V1__ehr.sql` | ⏳ |
| Domínio | `ClinicalVault`, `Patient`, `Encounter`, `SoapComposition` | ⏳ |
| FHIR | `FhirFacade`, perfis BR | ⏳ |
| Testes | `SoapSignIT`, `FhirValidationIT` (validador HAPI) | ⏳ |
