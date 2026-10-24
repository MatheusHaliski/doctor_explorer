# Matriz de conformidade

Requisito → norma → controle técnico → evidência → RF. Itens **⚖️ validar** dependem de parecer
jurídico/regulatório antes do go-live.

## CFM

| Requisito | Norma | Controle | Evidência | RF |
|---|---|---|---|---|
| Só médico inscrito e regular usa recursos médicos | Lei 3.268/1957; Res. CFM 2.145/2016 (CEM) | Selo `VERIFIED` por consulta à fonte do CFM, reverificação diária | `CredentialCheck`, `AuditEvent(credential.check)` | RF02 |
| RQE para anunciar especialidade | Res. CFM 2.336/2023 | Especialidade só aparece com RQE conferido | `SpecialtyClaim.confirmed` | RF02, RF03 |
| Publicidade médica | Res. CFM 2.336/2023 | Verificador de texto do perfil; moderação | `AdvertisingRuleChecker` | RF03 |
| Diretor-Técnico da plataforma | Res. CFM 2.147/2016 **⚖️** | Cadastro em `governance`, nome publicado | página "Sobre" | — |
| Sigilo profissional | CEM arts. 73–79 | Anonimização obrigatória, detector de identificadores, E2EE | `DeidentificationReport`, testes | RF04–07 |
| Prontuário e guarda de 20 anos | Res. 1.638/2002, 1.821/2007; Lei 13.787/2018 | PEP imutável e versionado, cofre com chave própria, Object Lock | `SoapComposition`, retenção S3 | RF08 |
| Prescrição eletrônica com ICP-Brasil | Res. CFM 2.299/2021; Lei 14.063/2020 | PAdES via provedor, QR e validador público | `SignedArtifact`, `/validar` | RF09 |
| Vedação de diagnóstico automatizado | Res. CFM 2.314/2022 (telemedicina) e CEM | Nenhuma saída de algoritmo para paciente; `cds` isolado e rotulado | ArchUnit: `cds` não expõe endpoint público | — |

## LGPD

| Requisito | Artigo | Controle | Evidência | RF |
|---|---|---|---|---|
| Base legal por tratamento | 7º, 11 | Registro de operações (`processing_activity`) | tabela publicada no RIPD | RF10 |
| Dado de saúde no PEP sem consentimento | 11, II, "f" | Base "tutela da saúde"; o titular é informado | texto na tela de exclusão | RF08, RF10 |
| Consentimento livre, destacado, por finalidade | 8º §§1º, 4º, 5º; 9º | Toggles desligados por padrão; histórico | `consent_event` | RF10 |
| Direitos do titular | 18 | Painel "Seus dados": acesso, correção, exportação, revogação, exclusão | endpoints `/privacy/*` | RF10 |
| Eliminação ao fim do tratamento, salvo obrigação legal | 15, 16 | Saga de eliminação + crypto-shredding; PEP preservado | `ErasureSaga`, recibo | RF10 |
| Segurança e boas práticas | 46, 49, 50 | TLS 1.3, AES-256-GCM, E2EE, Argon2id, 2FA, auditoria | pentest, ASVS | todos |
| Registro das operações | 37 | Auditoria append-only com hash encadeado | `audit_event` | RF13 |
| Encarregado | 41 | Canal, protocolo e prazo | `DpoTicket` | RF10 |
| Comunicação de incidente | 48; Res. CD/ANPD 15/2024 **⚖️** | Runbook com prazo de 3 dias úteis | `docs/runbooks/INCIDENTE.md` | — |
| Relatório de impacto | 38 | RIPD antes do go-live | `docs/legal/RIPD.md` | — |
| Transferência internacional | 33 | Dados em sa-east-1; backup secundário **⚖️** | ADR-011 | — |
| Privacy by Design | 46 §2º | Padrões no máximo de privacidade, minimização | revisão por RF | todos |

## Anvisa

| Requisito | Norma | Controle | Evidência |
|---|---|---|---|
| Enquadramento de software como dispositivo médico | RDC 657/2022; RDC 751/2022 | MVP sem algoritmo preditivo, triagem ou apoio diagnóstico; calculadoras fora | lista de funcionalidades do MVP |
| Isolamento de futuro SaMD | RDC 657/2022; IEC 62304; ISO 14971 | Contexto `cds` em serviço separado com ciclo de vida próprio | ADR-001 |
| Base de medicamentos | CMED/Anvisa | Catálogo versionado | `MedicationItem.anvisaCode` |

## Interoperabilidade e qualidade

| Requisito | Referência | Controle |
|---|---|---|
| FHIR R4 | HL7 FHIR 4.0.1; perfis RNDS | validador HAPI no CI |
| RNDS | Portaria GM/MS 1.434/2020 | credenciamento + certificado e-CNPJ |
| Terminologias | CID-10, LOINC, SNOMED CT **⚖️ licença** | `TerminologyService` |
| openEHR | CKM | arquétipos mapeados (ADR-003) |
| Qualidade de software | ISO/IEC 25010; SBIS NGS2 **⚖️ opcional** | metas em §5 da especificação |
| Acessibilidade | WCAG 2.2 AA; LBI 13.146/2015 | axe no CI, contraste por script |
