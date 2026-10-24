package com.doctorexplorer.core.identity.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Aceite dos Termos e da Política com versão, data, IP (HMAC) e user agent · LGPD Art. 7º, V e Art. 8º. */
@Entity
@Table(name = "terms_acceptance")
public class TermsAcceptance {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "terms_version", nullable = false)
    private String termsVersion;

    @Column(name = "privacy_version", nullable = false)
    private String privacyVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "ip_hash", nullable = false)
    private String ipHash;

    @Column(name = "user_agent")
    private String userAgent;

    protected TermsAcceptance() {}

    public TermsAcceptance(UUID userId, String termsVersion, String privacyVersion, String ipHash, String userAgent) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.acceptedAt = Instant.now();
        this.ipHash = ipHash;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255));
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTermsVersion() { return termsVersion; }
    public String getPrivacyVersion() { return privacyVersion; }
    public Instant getAcceptedAt() { return acceptedAt; }
}
