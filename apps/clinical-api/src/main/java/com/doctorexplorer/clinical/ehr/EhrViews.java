package com.doctorexplorer.clinical.ehr;

import com.doctorexplorer.clinical.terminology.Cid10;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Formatos de entrada e saída do prontuário (JSON da API). */
public final class EhrViews {

    private EhrViews() {}

    public record NewPatient(String name, String birthDate, String cpf, String sex, String localRecordNumber) {}

    public record PatientView(UUID id, String name, LocalDate birthDate, String cpf, String sex, String localRecordNumber, Instant lastRecordAt) {}

    public record Link(String type, String ref) {}

    public record NewComposition(UUID patientId, String context, String occurredAt, List<Link> links) {}

    public record AssessmentInput(String text, List<String> icd10, List<String> snomed) {}

    public record Draft(String subjective, String objective, AssessmentInput assessment, String plan, List<Link> links) {}

    public record AmendRequest(String reason) {}

    public record AssessmentView(String text, List<Cid10.Code> icd10, List<String> snomed) {}

    public record EncounterView(UUID id, String context, Instant occurredAt) {}

    public record AuthorView(UUID id, String name, String crm, String uf) {}

    public record Integrity(boolean valid, String algorithm) {}

    public record CompositionView(UUID id, UUID lineageId, int version, String state, UUID patientId, String patientName, EncounterView encounter,
            AuthorView author, String subjective, String objective, AssessmentView assessment, String plan, List<Link> links, String amendReason,
            UUID previousId, UUID supersededBy, Instant signedAt, String contentHash, String signature, String signingKeyId, Integrity integrity,
            Instant createdAt, Instant updatedAt, boolean editable, boolean amendable) {}

    public record CompositionSummary(UUID id, UUID lineageId, int version, String state, String context, Instant occurredAt, List<String> icd10,
            Instant signedAt, Instant updatedAt, UUID supersededBy) {}

    public record PatientDetail(PatientView patient, List<CompositionSummary> compositions) {}
}
