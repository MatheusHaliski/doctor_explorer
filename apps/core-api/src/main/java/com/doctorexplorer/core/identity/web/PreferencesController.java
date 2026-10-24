package com.doctorexplorer.core.identity.web;

import com.doctorexplorer.core.identity.domain.UserPreference;
import com.doctorexplorer.core.identity.repo.UserPreferences;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF01 · /api/v1/me/preferences: tema, tamanho da fonte, modo plantão e arte de fundo (Aparência). */
@RestController
@RequestMapping("/api/v1/me/preferences")
public class PreferencesController {

    /** Todos opcionais: o que vier nulo fica como está. */
    public record PreferencesUpdate(String theme, Integer fontScale, Boolean onCallMode, String background, Integer backgroundVeil) {}

    private final UserPreferences preferences;
    private final SessionSupport sessions;

    public PreferencesController(UserPreferences preferences, SessionSupport sessions) {
        this.preferences = preferences;
        this.sessions = sessions;
    }

    @GetMapping
    @Transactional
    Map<String, Object> get() {
        return view(load(sessions.require().userId()));
    }

    @PutMapping
    @Transactional
    Map<String, Object> put(@RequestBody PreferencesUpdate u) {
        UUID userId = sessions.require().userId();
        List<FieldProblem> problems = new ArrayList<>();
        String theme = u.theme() == null ? null : u.theme().trim().toLowerCase(Locale.ROOT);
        String background = u.background() == null ? null : u.background().trim().toLowerCase(Locale.ROOT);
        if (theme != null && !UserPreference.THEMES.contains(theme)) problems.add(new FieldProblem("theme", "Use system, light ou dark."));
        if (background != null && !UserPreference.BACKGROUNDS.contains(background)) {
            problems.add(new FieldProblem("background", "Arte desconhecida. Opções: " + String.join(", ", UserPreference.BACKGROUNDS) + "."));
        }
        if (u.backgroundVeil() != null && (u.backgroundVeil() < UserPreference.VEIL_MIN || u.backgroundVeil() > UserPreference.VEIL_MAX)) {
            problems.add(new FieldProblem("backgroundVeil", "A intensidade vai de " + UserPreference.VEIL_MIN + " a " + UserPreference.VEIL_MAX + "."));
        }
        if (u.fontScale() != null && (u.fontScale() < UserPreference.FONT_MIN || u.fontScale() > UserPreference.FONT_MAX)) {
            problems.add(new FieldProblem("fontScale", "O tamanho da fonte vai de " + UserPreference.FONT_MIN + " % a " + UserPreference.FONT_MAX + " %."));
        }
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        UserPreference p = load(userId);
        p.update(theme, u.fontScale(), u.onCallMode(), background, u.backgroundVeil());
        return view(preferences.save(p));
    }

    /** Contas criadas antes da tabela existir ganham a linha com os padrões na primeira leitura. */
    private UserPreference load(UUID userId) {
        return preferences.findById(userId).orElseGet(() -> preferences.save(new UserPreference(userId)));
    }

    private static Map<String, Object> view(UserPreference p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("locale", p.getLocale());
        v.put("theme", p.getTheme());
        v.put("fontScale", p.getFontScale());
        v.put("onCallMode", p.isOnCallMode());
        v.put("background", p.getBackground());
        v.put("backgroundVeil", p.getBackgroundVeil());
        v.put("backgrounds", UserPreference.BACKGROUNDS);
        v.put("updatedAt", p.getUpdatedAt().toString());
        return v;
    }
}
