package com.doctorexplorer.core.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Preferências de interface. Padrões: pt-BR, tema do sistema, fonte 100 %, modo plantão desligado, sem arte
 * de fundo (véu 45 % quando houver). Não são dado sensível; sincronizam entre aparelhos.
 */
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

    @Column(nullable = false)
    private String background = "none";

    @Column(name = "background_veil", nullable = false)
    private short backgroundVeil = 45;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static final Set<String> THEMES = Set.of("system", "light", "dark");
    /** Artes em apps/web/public/backgrounds (a ordem é a da tela de Aparência). */
    public static final List<String> BACKGROUNDS = List.of("none", "aurora-clara", "neon-escuro", "azul-noturno", "ambar", "neon-colorido");
    public static final int VEIL_MIN = 20, VEIL_MAX = 80, FONT_MIN = 85, FONT_MAX = 150;

    protected UserPreference() {}

    public UserPreference(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
    public String getLocale() { return locale; }
    public String getTheme() { return theme; }
    public short getFontScale() { return fontScale; }
    public boolean isOnCallMode() { return onCallMode; }
    public String getBackground() { return background; }
    public short getBackgroundVeil() { return backgroundVeil; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Aplica só os campos informados (null = mantém). Valores já validados pelo controlador. */
    public void update(String theme, Integer fontScale, Boolean onCallMode, String background, Integer backgroundVeil) {
        if (theme != null) this.theme = theme;
        if (fontScale != null) this.fontScale = fontScale.shortValue();
        if (onCallMode != null) this.onCallMode = onCallMode;
        if (background != null) this.background = background;
        if (backgroundVeil != null) this.backgroundVeil = backgroundVeil.shortValue();
        this.updatedAt = Instant.now();
    }
}
