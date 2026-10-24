package com.doctorexplorer.core.identity.service;

import com.doctorexplorer.core.config.AppSecretProperties;
import com.doctorexplorer.core.identity.domain.Credential;
import com.doctorexplorer.core.identity.domain.EmailVerification;
import com.doctorexplorer.core.identity.domain.TermsAcceptance;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.domain.UserPreference;
import com.doctorexplorer.core.identity.repo.Credentials;
import com.doctorexplorer.core.identity.repo.EmailVerifications;
import com.doctorexplorer.core.identity.repo.TermsAcceptances;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.repo.UserPreferences;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF01 · Cadastro: valida tudo de uma vez, cria a conta pendente, a DEK, o aceite e envia o código. */
@Service
public class RegistrationService {

    public static final int MIN_AGE = 18;

    public record RegisterCommand(
            String email, String displayName, LocalDate birthDate, String password,
            boolean acceptTerms, boolean acceptPrivacy, String termsVersion, String privacyVersion) {}

    private final UserAccounts accounts;
    private final Credentials credentials;
    private final EmailVerifications verifications;
    private final TermsAcceptances acceptances;
    private final UserPreferences preferences;
    private final UserKeyService userKeys;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder encoder;
    private final Mailer mailer;
    private final Validator validator;
    private final AppSecretProperties props;

    public RegistrationService(UserAccounts accounts, Credentials credentials, EmailVerifications verifications,
            TermsAcceptances acceptances, UserPreferences preferences, UserKeyService userKeys, PasswordPolicy passwordPolicy,
            PasswordEncoder encoder, Mailer mailer, Validator validator, AppSecretProperties props) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.verifications = verifications;
        this.acceptances = acceptances;
        this.preferences = preferences;
        this.userKeys = userKeys;
        this.passwordPolicy = passwordPolicy;
        this.encoder = encoder;
        this.mailer = mailer;
        this.validator = validator;
        this.props = props;
    }

    @Transactional
    public UserAccount register(Object request, RegisterCommand cmd, String ipHash, String userAgent) {
        List<FieldProblem> problems = new ArrayList<>();
        for (ConstraintViolation<Object> v : validator.validate(request)) {
            problems.add(new FieldProblem(v.getPropertyPath().toString(), v.getMessage()));
        }
        if (cmd.birthDate() != null && Period.between(cmd.birthDate(), LocalDate.now()).getYears() < MIN_AGE) {
            problems.add(new FieldProblem("birthDate", "O Doctor Explorer é para profissionais com 18 anos ou mais."));
        }
        if (!cmd.acceptTerms()) problems.add(new FieldProblem("acceptTerms", "É preciso aceitar os Termos de Uso."));
        if (!cmd.acceptPrivacy()) problems.add(new FieldProblem("acceptPrivacy", "É preciso aceitar a Política de Privacidade."));
        if (cmd.termsVersion() != null && !cmd.termsVersion().equals(props.terms().version())) {
            problems.add(new FieldProblem("termsVersion", "Os Termos foram atualizados; recarregue a página para ler a versão vigente."));
        }
        if (cmd.privacyVersion() != null && !cmd.privacyVersion().equals(props.terms().privacyVersion())) {
            problems.add(new FieldProblem("privacyVersion", "A Política de Privacidade foi atualizada; recarregue a página."));
        }
        for (String p : passwordPolicy.validate(cmd.password(), cmd.email(), cmd.displayName())) {
            problems.add(new FieldProblem("password", p));
        }
        if (cmd.email() != null && accounts.existsByEmailNormalized(UserAccount.normalizeEmail(cmd.email()))) {
            problems.add(new FieldProblem("email", "Já existe uma conta com este e-mail. Entre ou recupere a senha."));
        }
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);

        UserAccount account = accounts.save(UserAccount.register(cmd.email(), cmd.displayName(), cmd.birthDate()));
        credentials.save(new Credential(account.getId(), encoder.encode(cmd.password())));
        userKeys.create(account.getId());
        preferences.save(new UserPreference(account.getId()));
        acceptances.save(new TermsAcceptance(account.getId(), cmd.termsVersion(), cmd.privacyVersion(), ipHash, userAgent));
        sendVerification(account, EmailVerification.issue(account.getId()));
        return account;
    }

    @Transactional
    public void resendVerification(UUID userId) {
        UserAccount account = accounts.findById(userId).orElseThrow();
        EmailVerification v = verifications.findById(userId).orElseThrow(() ->
                new ApiProblemException(HttpStatus.CONFLICT, "email-ja-confirmado", "E-mail já confirmado", "Seu e-mail já foi confirmado."));
        if (!v.canResend(Instant.now())) {
            long wait = EmailVerification.RESEND_INTERVAL.toSeconds() - (Instant.now().getEpochSecond() - v.getSentAt().getEpochSecond());
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "reenvio-cedo", "Aguarde para reenviar",
                    "Você pode pedir um novo código em " + Math.max(wait, 1) + " s.", java.util.Map.of("retryAfter", Math.max(wait, 1)));
        }
        sendVerification(account, v.renew());
    }

    @Transactional
    public UserAccount verifyEmail(UUID userId, String code) {
        UserAccount account = accounts.findById(userId).orElseThrow();
        EmailVerification v = verifications.findById(userId).orElseThrow(() ->
                new ApiProblemException(HttpStatus.CONFLICT, "email-ja-confirmado", "E-mail já confirmado", "Seu e-mail já foi confirmado."));
        switch (v.verify(code, Instant.now())) {
            case OK -> {
                account.activate();
                verifications.delete(v);
                return accounts.save(account);
            }
            case EXPIRED -> throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "codigo-expirado", "Código expirado",
                    "O código venceu. Peça um novo código.");
            case TOO_MANY_ATTEMPTS -> throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "codigo-bloqueado", "Muitas tentativas",
                    "Você errou o código 5 vezes. Peça um novo código.", java.util.Map.of("retryAfter", 60));
            default -> {
                verifications.save(v);
                throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "codigo-incorreto", "Código incorreto",
                        "Confira os 6 dígitos do e-mail. Restam " + (EmailVerification.MAX_ATTEMPTS - v.getAttempts()) + " tentativa(s).");
            }
        }
    }

    private void sendVerification(UserAccount account, EmailVerification.Issued issued) {
        verifications.save(issued.verification());
        mailer.send(account.getEmail(), "Seu código de confirmação · Doctor Explorer",
                "Olá, " + account.getDisplayName() + ".\n\nSeu código é " + issued.code() + " e vale por 30 minutos.\n"
                        + "Se você não criou uma conta no Doctor Explorer, ignore este e-mail.");
    }
}
