# Diagrama de classes (modelo de domínio consolidado)

Visão por contexto. Os detalhes de cada classe estão no RF correspondente.

## core-api

```mermaid
classDiagram
    direction LR
    class UserAccount {
      +UUID id
      +String email
      +AccountStatus status
    }
    class Credential
    class MfaSecret
    class UserKey {
      +byte[] wrappedDek
    }
    class ProfessionalProfile {
      +String crm
      +String crmUf
      +SealStatus seal
      +Visibility visibility
    }
    class SpecialtyClaim {
      +String specialtyCode
      +String rqe
    }
    class CredentialCheck
    class ConsentEvent {
      +ConsentPurpose purpose
      +boolean granted
    }
    class TermsAcceptance
    class DeletionRequest
    class Institution {
      +String cnpj
      +String cnes
      +InstitutionType type
      +Point location
    }
    class Membership {
      +InstitutionRole role
      +MembershipState state
    }
    class SpecialtyGroup
    class GroupMembership
    class CasePost {
      +PostState state
    }
    class Comment
    class MediaAsset {
      +MediaState state
    }
    class DeidentificationReport
    class Conversation {
      +String mlsGroupId
      +long epoch
    }
    class Device
    class CipherMessage
    class Call
    class AuditEvent {
      +String prevHash
      +String hash
    }

    UserAccount "1" *-- "1" Credential
    UserAccount "1" *-- "0..1" MfaSecret
    UserAccount "1" *-- "1" UserKey
    UserAccount "1" -- "0..1" ProfessionalProfile
    UserAccount "1" *-- "*" ConsentEvent
    UserAccount "1" *-- "1..*" TermsAcceptance
    UserAccount "1" -- "0..1" DeletionRequest
    UserAccount "1" *-- "*" Device
    ProfessionalProfile "1" *-- "*" SpecialtyClaim
    ProfessionalProfile "1" -- "*" CredentialCheck
    Institution "0..1" <-- "*" Institution : parent
    Institution "1" *-- "*" Membership
    UserAccount "1" -- "*" Membership
    SpecialtyGroup "1" *-- "*" GroupMembership
    UserAccount "1" -- "*" GroupMembership
    SpecialtyGroup "1" *-- "*" CasePost
    CasePost "1" *-- "*" Comment
    CasePost "1" -- "*" MediaAsset
    MediaAsset "1" -- "0..1" DeidentificationReport
    Conversation "1" *-- "*" CipherMessage
    Conversation "1" -- "*" Call
    UserAccount "1" -- "*" AuditEvent : ator
```

## clinical-api

```mermaid
classDiagram
    direction LR
    class ClinicalVault {
      +VaultOwnerType ownerType
      +UUID ownerId
      +UUID keyId
    }
    class Patient {
      +EncryptedField name
      +EncryptedField cpf
    }
    class Encounter
    class SoapComposition {
      +int version
      +CompositionState state
    }
    class DiscussionLink {
      +LinkType type
      +UUID opaqueRef
    }
    class ClinicalDocument {
      <<abstract>>
      +DocumentState state
    }
    class MedicationRequest
    class ServiceRequest
    class SignedArtifact {
      +String sha256
      +String validationCode
    }
    class Signature
    class TerminologyCode {
      +String system
      +String code
    }
    ClinicalVault "1" *-- "*" Patient
    Patient "1" *-- "*" Encounter
    Encounter "1" *-- "1..*" SoapComposition
    Encounter "1" *-- "*" ClinicalDocument
    SoapComposition "1" *-- "*" DiscussionLink
    SoapComposition "1" -- "0..1" Signature
    ClinicalDocument <|-- MedicationRequest
    ClinicalDocument <|-- ServiceRequest
    ClinicalDocument "1" -- "0..1" SignedArtifact
    SoapComposition "*" -- "*" TerminologyCode
    ServiceRequest "*" -- "*" TerminologyCode
```

A referência da `core-api` para a `clinical-api` é sempre por **id opaco** (`DiscussionLink.opaqueRef`);
a `clinical-api` nunca guarda e-mail, nome de usuário ou qualquer dado social.
