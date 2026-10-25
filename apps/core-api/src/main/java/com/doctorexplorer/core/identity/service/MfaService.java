package com.doctorexplorer.core.identity.service;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.common.AesGcm;
import com.doctorexplorer.core.common.Hashing;
import com.doctorexplorer.core.config.AppSecretProperties;
import com.doctorexplorer.core.identity.domain.MfaSecret;
import com.doctorexplorer.core.identity.domain.RecoveryCode;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.MfaSecrets;
import com.doctorexplorer.core.identity.repo.RecoveryCodes;
import com.doctorexplorer.core.web.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF01 · 2FA: TOTP + 10 códigos de recuperação de uso único. Segredo cifrado com a subchave "mfa". */
@Service
public class MfaService {

    public static final int RECOVERY_CODES = 10;
    public static final String ISSUER = "Doctor Explorer";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // sem 0/o, 1/l/i

    public enum Verification { TOTP, RECOVERY_CODE, INVALID }

    public record Setup(String otpauthUri, String secretBase32) {}

    private final MfaSecrets secrets;
    private final RecoveryCodes recoveryCodes;
    private final byte[] key;

    private final AuditLog audit;

    public MfaService(MfaSecrets secrets, RecoveryCodes recoveryCodes, AppSecretProperties props, AuditLog audit) {
        this.secrets = secrets;
        this.recoveryCodes = recoveryCodes;
        this.audit = audit;
        this.key = props.subkey("mfa");
    }

    public boolean isEnabled(UUID userId) {
        return secrets.findById(userId).map(MfaSecret::isEnabled).orElse(false);
    }

    /** Gera um segredo pendente (ou substitui um pendente anterior). Não mexe num 2FA já ativo. */
    @Transactional
    public Setup beginSetup(UserAccount account) {
        secrets.findById(account.getId()).filter(MfaSecret::isEnabled).ifPresent(s -> {
            throw new ApiProblemException(HttpStatus.CONFLICT, "2fa-ja-ativo", "2FA já ativo", "Desative o 2FA atual antes de configurar outro.");
        });
        byte[] secret = Totp.newSecret();
        secrets.save(new MfaSecret(account.getId(), AesGcm.encrypt(key, secret, aad(account.getId()))));
        return new Setup(Totp.otpauthUri(secret, ISSUER, account.getEmail()), Totp.base32(secret));
    }

    /** Confirma com o primeiro código do app; ativa e devolve os códigos de recuperação (mostrados uma vez). */
    @Transactional
    public List<String> confirmSetup(UUID userId, String code) {
        MfaSecret s = secrets.findById(userId).orElseThrow(() ->
                new ApiProblemException(HttpStatus.CONFLICT, "2fa-sem-setup", "Configuração não iniciada", "Comece pela geração do QR code."));
        if (s.isEnabled()) throw new ApiProblemException(HttpStatus.CONFLICT, "2fa-ja-ativo", "2FA já ativo", "O 2FA já está ativo nesta conta.");
        if (!Totp.verify(decrypt(s), code, Instant.now().getEpochSecond())) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "codigo-incorreto", "Código incorreto",
                    "Confira o código no aplicativo autenticador e tente de novo.");
        }
        s.confirm();
        audit.record(AuditRecord.of("auth.mfa_enable").actor(userId, "").resource("user_account", userId));
        secrets.save(s);
        return regenerateRecoveryCodes(userId);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId) {
        audit.record(AuditRecord.of("auth.mfa_recovery_regenerate").actor(userId, "").resource("user_account", userId));
        recoveryCodes.deleteByUserId(userId);
        List<String> plain = new ArrayList<>(RECOVERY_CODES);
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String c = randomCode();
            plain.add(c);
            recoveryCodes.save(new RecoveryCode(userId, hash(userId, normalize(c))));
        }
        return plain;
    }

    /** No login e no sudo: TOTP (janela ±1) ou código de recuperação, que é consumido. */
    @Transactional
    public Verification verify(UUID userId, String code) {
        MfaSecret s = secrets.findById(userId).filter(MfaSecret::isEnabled).orElse(null);
        if (s == null || code == null || code.isBlank()) return Verification.INVALID;
        if (Totp.verify(decrypt(s), code, Instant.now().getEpochSecond())) return Verification.TOTP;
        String h = hash(userId, normalize(code));
        for (RecoveryCode rc : recoveryCodes.findByUserIdAndUsedAtIsNull(userId)) {
            if (rc.getCodeHash().equals(h)) {
                rc.use(Instant.now());
                recoveryCodes.save(rc);
                return Verification.RECOVERY_CODE;
            }
        }
        return Verification.INVALID;
    }

    public long remainingRecoveryCodes(UUID userId) {
        return recoveryCodes.countByUserIdAndUsedAtIsNull(userId);
    }

    @Transactional
    public void disable(UUID userId) {
        recoveryCodes.deleteByUserId(userId);
        secrets.deleteById(userId);
        audit.record(AuditRecord.of("auth.mfa_disable").actor(userId, "").resource("user_account", userId));
    }

    private byte[] decrypt(MfaSecret s) {
        return AesGcm.decrypt(key, s.getTotpSecretCipher(), aad(s.getUserId()));
    }

    private static byte[] aad(UUID userId) {
        return ("mfa:" + userId).getBytes(StandardCharsets.UTF_8);
    }

    private static String hash(UUID userId, String code) {
        return Hashing.sha256Hex(userId + ":recovery:" + code);
    }

    /** O usuário pode digitar com ou sem hífen/espaços e em qualquer caixa. */
    private static String normalize(String code) {
        return code.trim().toLowerCase().replace("-", "").replace(" ", "");
    }

    /** 10 caracteres em dois grupos (ex.: "k7m2p-q9x4e"): fácil de ditar, 50 bits de entropia. */
    private static String randomCode() {
        StringBuilder b = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) b.append('-');
            b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return b.toString();
    }
}
