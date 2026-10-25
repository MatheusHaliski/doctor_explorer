package com.doctorexplorer.clinical.documents;

import com.doctorexplorer.clinical.documents.DocumentViews.MedicationInput;
import java.util.Map;

/** Posologia em português a partir dos campos estruturados ("1 comprimido por via oral, de 8 em 8 horas, por 7 dias."). */
public final class Posology {

    private Posology() {}

    public static final Map<String, String> ROUTES = Map.ofEntries(Map.entry("ORAL", "oral"), Map.entry("SUBLINGUAL", "sublingual"),
            Map.entry("TOPICA", "tópica"), Map.entry("OFTALMICA", "oftálmica"), Map.entry("OTOLOGICA", "otológica"), Map.entry("NASAL", "nasal"),
            Map.entry("INALATORIA", "inalatória"), Map.entry("RETAL", "retal"), Map.entry("VAGINAL", "vaginal"), Map.entry("INTRAMUSCULAR", "intramuscular"),
            Map.entry("INTRAVENOSA", "intravenosa"), Map.entry("SUBCUTANEA", "subcutânea"), Map.entry("TRANSDERMICA", "transdérmica"));

    /** Unidade no singular → plural. */
    public static final Map<String, String> UNITS = Map.ofEntries(Map.entry("comprimido", "comprimidos"), Map.entry("cápsula", "cápsulas"),
            Map.entry("drágea", "drágeas"), Map.entry("mL", "mL"), Map.entry("gota", "gotas"), Map.entry("sachê", "sachês"), Map.entry("aplicação", "aplicações"),
            Map.entry("jato", "jatos"), Map.entry("ampola", "ampolas"), Map.entry("unidade", "unidades"), Map.entry("g", "g"));

    public static String text(MedicationInput m) {
        StringBuilder sb = new StringBuilder();
        String dose = m.dose().trim();
        boolean one = dose.equals("1") || dose.equals("1,0") || dose.matches("0([.,]\\d+)?") || dose.matches("1/\\d");
        sb.append(dose).append(' ').append(one ? m.doseUnit() : UNITS.getOrDefault(m.doseUnit(), m.doseUnit()));
        sb.append(" por via ").append(ROUTES.getOrDefault(m.route(), m.route().toLowerCase()));
        if (m.intervalHours() != null) {
            sb.append(", de ").append(m.intervalHours()).append(" em ").append(m.intervalHours()).append(m.intervalHours() == 1 ? " hora" : " horas");
        } else if (m.timesPerDay() != null) {
            sb.append(", ").append(m.timesPerDay() == 1 ? "1 vez" : m.timesPerDay() + " vezes").append(" ao dia");
        }
        if (Boolean.TRUE.equals(m.continuous())) {
            sb.append(", uso contínuo");
        } else if (m.durationDays() != null) {
            sb.append(", por ").append(m.durationDays()).append(m.durationDays() == 1 ? " dia" : " dias");
        }
        sb.append('.');
        if (m.instructions() != null && !m.instructions().isBlank()) sb.append(' ').append(m.instructions().trim());
        return sb.toString();
    }
}
