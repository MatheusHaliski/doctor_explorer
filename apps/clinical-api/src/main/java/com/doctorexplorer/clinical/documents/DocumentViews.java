package com.doctorexplorer.clinical.documents;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Formatos de entrada e saída de receita e pedido de exame. */
public final class DocumentViews {

    private DocumentViews() {}

    /** Posologia estruturada (vira FHIR Dosage): dose, via, frequência por intervalo ou vezes ao dia, duração. */
    public record MedicationInput(String ggrem, String dose, String doseUnit, String route, Integer intervalHours, Integer timesPerDay, Integer durationDays,
            Boolean continuous, String quantity, String instructions) {}

    public record ExamInput(String loinc) {}

    public record Content(List<MedicationInput> medications, List<ExamInput> exams, String indication, String notes) {}

    public record NewDocument(UUID patientId, String kind, UUID encounterId) {}

    public record SignRequest(String reviewedHash) {}

    public record DeliverRequest(String channel, String destination) {}

    public record CancelRequest(String reason) {}

    public record TemplateInput(String kind, String name, Content content) {}

    public record MedicationView(MedicationInput input, String label, String substance, String presentation, boolean antimicrobial, String posology) {}

    public record ExamView(String loinc, String name, String tuss, String tussTerm) {}

    public record DocumentView(UUID id, String kind, String state, UUID patientId, String patientName, UUID encounterId, List<MedicationView> medications,
            List<ExamView> exams, String indication, String notes, String contentHash, String validationCode, String validationUrl, Instant signedAt,
            LocalDate validUntil, String signatureLevel, String signatureProvider, String certSubject, String pdfSha256, String failureReason,
            String cancelReason, Instant canceledAt, List<DeliveryView> deliveries, Instant createdAt, Instant updatedAt) {}

    public record DeliveryView(String channel, String destination, Instant expiresAt, Instant usedAt, Instant createdAt) {}

    public record Delivered(String link, String channel, String destination, Instant expiresAt) {}

    public record MyDocument(UUID id, String kind, String state, UUID patientId, String patientName, int items, Instant signedAt, LocalDate validUntil,
            Instant createdAt) {}

    public record DocumentSummary(UUID id, String kind, String state, int items, Instant signedAt, LocalDate validUntil, Instant createdAt) {}

    /** O que a farmácia vê no validador público: o mínimo para dispensar com segurança. */
    public record PublicView(String kind, String status, String statusText, Instant issuedAt, LocalDate validUntil, String doctorName, String crm, String uf,
            String patientInitials, List<String> items, boolean signatureValid, String signatureLevel, boolean simulated, String pdfSha256, Instant canceledAt) {}
}
