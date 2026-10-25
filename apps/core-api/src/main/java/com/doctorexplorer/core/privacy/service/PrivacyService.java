package com.doctorexplorer.core.privacy.service;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.config.AppSecretProperties;
import com.doctorexplorer.core.credentialing.repo.ProfessionalProfiles;
import com.doctorexplorer.core.credentialing.repo.SpecialtyClaims;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.TermsAcceptances;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.repo.UserPreferences;
import com.doctorexplorer.core.identity.service.Mailer;
import com.doctorexplorer.core.privacy.domain.ConsentEvent;
import com.doctorexplorer.core.privacy.domain.ConsentPurpose;
import com.doctorexplorer.core.privacy.domain.DataExportRequest;
import com.doctorexplorer.core.privacy.domain.DpoTicket;
import com.doctorexplorer.core.privacy.repo.ConsentEvents;
import com.doctorexplorer.core.privacy.repo.DataExportRequests;
import com.doctorexplorer.core.privacy.repo.DpoTickets;
import com.doctorexplorer.core.web.ApiProblemException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** RF10 · Direitos do titular: consentimentos, "seus dados", exportação, exclusão com carência, Encarregado. */
@Service
public class PrivacyService {

    public record ConsentView(ConsentPurpose purpose, boolean granted, Instant changedAt) {}

    /** Operadores com quem dados pessoais podem ser compartilhados (Art. 18, VII). Mantido em código até o RIPD. */
    public static final List<Map<String, String>> OPERATORS = List.of(
            Map.of("nome", "Provedor de nuvem (AWS, região sa-east-1)", "finalidade", "hospedagem e armazenamento cifrado", "dados", "todos, cifrados em repouso"),
            Map.of("nome", "Serviço de e-mail transacional", "finalidade", "códigos de confirmação e avisos de segurança", "dados", "e-mail e nome"),
            Map.of("nome", "Fonte oficial do CFM", "finalidade", "verificação de CRM/RQE", "dados", "CRM, UF e nome"));

    private final ConsentEvents consents;
    private final DataExportRequests exports;
    private final DpoTickets tickets;
    private final UserAccounts accounts;
    private final UserPreferences preferences;
    private final TermsAcceptances acceptances;
    private final ProfessionalProfiles profiles;
    private final SpecialtyClaims claims;
    private final Mailer mailer;
    private final AppSecretProperties props;
    private final ObjectMapper json = new ObjectMapper();

    private final AuditLog audit;

    public PrivacyService(ConsentEvents consents, DataExportRequests exports, DpoTickets tickets, UserAccounts accounts,
            UserPreferences preferences, TermsAcceptances acceptances, ProfessionalProfiles profiles, SpecialtyClaims claims,
            Mailer mailer, AppSecretProperties props, AuditLog audit) {
        this.consents = consents;
        this.exports = exports;
        this.tickets = tickets;
        this.accounts = accounts;
        this.preferences = preferences;
        this.acceptances = acceptances;
        this.profiles = profiles;
        this.claims = claims;
        this.mailer = mailer;
        this.props = props;
        this.audit = audit;
    }

    /** Estado atual por finalidade: o evento mais recente; sem evento = desligado (padrão de privacidade). */
    @Transactional(readOnly = true)
    public List<ConsentView> consents(UUID userId) {
        Map<ConsentPurpose, ConsentView> current = new EnumMap<>(ConsentPurpose.class);
        for (ConsentPurpose p : ConsentPurpose.values()) current.put(p, new ConsentView(p, false, null));
        List<ConsentEvent> history = consents.findByUserIdOrderByOccurredAtDesc(userId);
        for (ConsentEvent e : history) {
            if (current.get(e.getPurpose()).changedAt() == null) current.put(e.getPurpose(), new ConsentView(e.getPurpose(), e.isGranted(), e.getOccurredAt()));
        }
        return List.copyOf(current.values());
    }

    /** Ligar e revogar usam o mesmo controle; cada mudança vira um evento com versão do texto e IP pseudonimizado. */
    @Transactional
    public ConsentView setConsent(UUID userId, ConsentPurpose purpose, boolean granted, String ipHash) {
        ConsentEvent e = consents.save(new ConsentEvent(userId, purpose, granted, props.terms().privacyVersion(), ipHash));
        audit.record(AuditRecord.of("privacy.consent_change").actor(userId, "").resource("consent", purpose).detail("granted", granted).ip(ipHash));
        return new ConsentView(purpose, e.isGranted(), e.getOccurredAt());
    }

    @Transactional(readOnly = true)
    public List<ConsentEvent> consentHistory(UUID userId) {
        return consents.findByUserIdOrderByOccurredAtDesc(userId);
    }

    /** "Seus dados": o que guardamos, por categoria, em linguagem simples. */
    @Transactional(readOnly = true)
    public Map<String, Object> myData(UUID userId) {
        UserAccount a = accounts.findById(userId).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("formatVersion", "1");
        out.put("exportedAt", Instant.now().toString());
        out.put("conta", Map.of("id", a.getId().toString(), "nome", a.getDisplayName(), "email", a.getEmail(),
                "nascimento", a.getBirthDate().toString(), "status", a.getStatus().name(), "criadaEm", a.getCreatedAt().toString()));
        preferences.findById(userId).ifPresent(p -> out.put("preferencias", Map.of("idioma", p.getLocale(), "tema", p.getTheme(),
                "escalaFonte", p.getFontScale(), "modoPlantao", p.isOnCallMode(), "arteDeFundo", p.getBackground(), "intensidadeDoVeu", p.getBackgroundVeil())));
        profiles.findById(userId).ifPresent(p -> out.put("perfilProfissional", Map.of("crm", String.valueOf(p.getCrmNumber()), "uf", String.valueOf(p.getCrmUf()),
                "selo", p.getSeal().name(), "visibilidade", p.getVisibility().name(),
                "especialidades", claims.findByUserId(userId).stream().map(c -> Map.of("codigo", c.getSpecialtyCode(), "rqe", c.getRqe(), "confirmada", c.isConfirmed())).toList())));
        out.put("consentimentos", consents(userId).stream().map(c -> Map.of("finalidade", c.purpose().name(), "ligado", c.granted(),
                "alteradoEm", c.changedAt() == null ? "" : c.changedAt().toString())).toList());
        out.put("aceitesDeTermos", acceptances.findAll().stream().filter(t -> t.getUserId().equals(userId))
                .map(t -> Map.of("termos", t.getTermsVersion(), "privacidade", t.getPrivacyVersion(), "em", t.getAcceptedAt().toString())).toList());
        out.put("compartilhamento", OPERATORS);
        out.put("registrosClinicos", "Notas, receitas e pedidos de exame ficam na clinical-api e seguem a retenção legal de 20 anos (Lei 13.787/2018); não entram na eliminação da conta.");
        return out;
    }

    /** Art. 18, V · exportação em JSON versionado, disponível por 24 h. ZIP cifrado por URL pré-assinada entra com o S3 (RF05). */
    @Transactional
    public DataExportRequest requestExport(UUID userId) {
        try {
            DataExportRequest r = exports.save(new DataExportRequest(userId, json.writeValueAsString(myData(userId))));
            audit.record(AuditRecord.of("privacy.export_request").actor(userId, "").resource("data_export", r.getId()));
            return r;
        } catch (RuntimeException e) {
            throw new ApiProblemException(HttpStatus.INTERNAL_SERVER_ERROR, "exportacao-falhou", "Não foi possível gerar a cópia",
                    "Tente de novo em alguns minutos ou fale com o Encarregado.");
        }
    }

    @Transactional
    public DataExportRequest download(UUID userId, UUID exportId) {
        DataExportRequest r = exports.findById(exportId).filter(x -> x.getUserId().equals(userId)).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "A cópia não existe ou já expirou."));
        if (!r.isAvailable(Instant.now())) {
            throw new ApiProblemException(HttpStatus.GONE, "exportacao-expirada", "Cópia expirada", "A cópia ficou disponível por 24 h. Peça uma nova.");
        }
        r.markDownloaded();
        return exports.save(r);
    }

    /** Art. 18, VI · pede exclusão: 30 dias de carência, aviso por e-mail com a data final. Registros clínicos ficam. */
    @Transactional
    public UserAccount requestDeletion(UUID userId) {
        UserAccount a = accounts.findById(userId).orElseThrow();
        a.requestDeletion(Instant.now());
        accounts.save(a);
        audit.record(AuditRecord.of("privacy.deletion_request").actor(userId, "").resource("user_account", userId).detail("graceEndsAt", a.getGraceEndsAt()));
        String until = DateTimeFormatter.ofPattern("dd/MM/yyyy").format(a.getGraceEndsAt().atOffset(ZoneOffset.UTC));
        mailer.send(a.getEmail(), "Pedido de exclusão recebido · Doctor Explorer",
                "Olá, " + a.getDisplayName() + ".\n\nRecebemos seu pedido de exclusão. Você pode reativar a conta entrando de novo até "
                        + until + ". Depois disso, perfil, publicações e mensagens serão eliminados. Registros clínicos (notas, receitas, pedidos) "
                        + "ficam guardados por 20 anos por obrigação legal.\n\nSe não foi você, entre agora e troque a senha.");
        return a;
    }

    @Transactional
    public DpoTicket openTicket(UUID userId, DpoTicket.Type type, String message) {
        if (message == null || message.isBlank()) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "mensagem-vazia", "Descreva o pedido", "Escreva o que você precisa; não inclua dados de pacientes.");
        }
        DpoTicket t = tickets.save(new DpoTicket(userId, type, message.trim()));
        audit.record(AuditRecord.of("privacy.dpo_request").actor(userId, "").resource("dpo_ticket", t.getProtocol()).detail("type", type));
        return t;
    }

    @Transactional(readOnly = true)
    public List<DpoTicket> myTickets(UUID userId) {
        return tickets.findByUserIdOrderByOpenedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<DpoTicket> openTickets() {
        return tickets.findByStateOrderByDueAtAsc(DpoTicket.State.OPEN);
    }

    @Transactional
    public DpoTicket answerTicket(UUID ticketId, String answer) {
        DpoTicket t = tickets.findById(ticketId).orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "Pedido não encontrado."));
        t.answer(answer);
        audit.record(AuditRecord.of("privacy.dpo_answer").resource("dpo_ticket", t.getProtocol()));
        return tickets.save(t);
    }
}
