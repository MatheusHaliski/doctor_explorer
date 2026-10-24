package com.doctorexplorer.core.identity.domain;

import com.doctorexplorer.core.common.Hashing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Código de 6 dígitos: 30 min, 5 tentativas, reenvio 1/min. Só o hash fica no banco. */
@Entity
@Table(name = "email_verification")
public class EmailVerification {

    public static final Duration VALIDITY = Duration.ofMinutes(30);
    public static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    public static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Outcome { OK, WRONG_CODE, EXPIRED, TOO_MANY_ATTEMPTS }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected EmailVerification() {}

    private EmailVerification(UUID userId) {
        this.userId = userId;
    }

    /** Cria (ou renova) e devolve o código em claro para envio; o objeto guarda só o hash. */
    public static Issued issue(UUID userId) {
        EmailVerification v = new EmailVerification(userId);
        return v.renew();
    }

    public Issued renew() {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        this.codeHash = hash(userId, code);
        this.attempts = 0;
        this.sentAt = Instant.now();
        this.expiresAt = sentAt.plus(VALIDITY);
        return new Issued(this, code);
    }

    public boolean canResend(Instant now) {
        return now.isAfter(sentAt.plus(RESEND_INTERVAL));
    }

    public Outcome verify(String code, Instant now) {
        if (now.isAfter(expiresAt)) return Outcome.EXPIRED;
        if (attempts >= MAX_ATTEMPTS) return Outcome.TOO_MANY_ATTEMPTS;
        attempts++;
        if (!codeHash.equals(hash(userId, code == null ? "" : code.trim()))) {
            return attempts >= MAX_ATTEMPTS ? Outcome.TOO_MANY_ATTEMPTS : Outcome.WRONG_CODE;
        }
        return Outcome.OK;
    }

    private static String hash(UUID userId, String code) {
        return Hashing.sha256Hex(userId + ":" + code);
    }

    public record Issued(EmailVerification verification, String code) {}

    public UUID getUserId() { return userId; }
    public int getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
