package com.doctorexplorer.core.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Preferências de interface. Padrões: pt-BR, tema do sistema, fonte 100 %, modo plantão desligado. */
@Entity
@Table(name = "user_preference")
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String locale = "pt-BR";

    @Column(nullable = false)
    private String theme = "system";

    @Column(name = "font_scale", nullable = false)
    private short fontScale = 100;

    @Column(name = "on_call_mode", nullable = false)
    private boolean onCallMode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPreference() {}

    public UserPreference(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
    public String getLocale() { return locale; }
    public String getTheme() { return theme; }
    public short getFontScale() { return fontScale; }
    public boolean isOnCallMode() { return onCallMode; }
}
