package com.doctorexplorer.core.privacy.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Art. 41 · pedido do titular ao Encarregado, com protocolo e prazo (15 dias, ⚖️ Q13). */
@Entity
@Table(name = "dpo_ticket")
public class DpoTicket {

    public static final Duration SLA = Duration.ofDays(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Type { ACCESS, CORRECTION, ANONYMIZATION, PORTABILITY, DELETION, SHARING_INFO, CONSENT_INFO, REVOCATION, INCIDENT, OTHER }

    public enum State { OPEN, ANSWERED, CLOSED }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String protocol;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column
    private String answer;

    protected DpoTicket() {}

    public DpoTicket(UUID userId, Type type, String message) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.openedAt = Instant.now();
        this.dueAt = openedAt.plus(SLA);
        this.protocol = "DPO-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(openedAt.atOffset(ZoneOffset.UTC)) + "-" + String.format("%05d", RANDOM.nextInt(100_000));
    }

    public void answer(String text) {
        this.answer = text;
        this.answeredAt = Instant.now();
        this.state = State.ANSWERED;
    }

    public UUID getId() { return id; }
    public String getProtocol() { return protocol; }
    public UUID getUserId() { return userId; }
    public Type getType() { return type; }
    public String getMessage() { return message; }
    public State getState() { return state; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getDueAt() { return dueAt; }
    public String getAnswer() { return answer; }
}
