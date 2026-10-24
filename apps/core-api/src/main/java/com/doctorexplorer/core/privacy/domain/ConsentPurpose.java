package com.doctorexplorer.core.privacy.domain;

/** Finalidades com consentimento (todas desligadas por padrão). Dado clínico não usa consentimento (Art. 11, II, "f"). */
public enum ConsentPurpose {
    LOCATION("Localização", "Usar sua localização para ordenar instituições por distância.", "Art. 7º, I"),
    REMINDERS("Lembretes", "Avisar sobre casos sem resposta e prazos de parecer.", "Art. 7º, I"),
    CONTENT_RECOMMENDATION("Recomendação de conteúdo", "Sugerir casos e grupos a partir da sua especialidade e atividade.", "Art. 11, I"),
    INSTITUTIONAL_COMMUNICATION("Comunicação institucional", "Receber comunicados das instituições às quais você está vinculado por e-mail.", "Art. 7º, I");

    public final String title;
    public final String description;
    public final String legalBasis;

    ConsentPurpose(String title, String description, String legalBasis) {
        this.title = title;
        this.description = description;
        this.legalBasis = legalBasis;
    }
}
