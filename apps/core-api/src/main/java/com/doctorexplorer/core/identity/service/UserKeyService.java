package com.doctorexplorer.core.identity.service;

import com.doctorexplorer.core.common.AesGcm;
import com.doctorexplorer.core.config.AppSecretProperties;
import com.doctorexplorer.core.identity.domain.UserKey;
import com.doctorexplorer.core.identity.repo.UserKeys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Cria, abre e destrói a DEK social por usuário. A KEK vem da subchave "user-kek" (KMS em produção). */
@Service
public class UserKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEK_VERSION = 1;

    private final UserKeys keys;
    private final byte[] kek;

    public UserKeyService(UserKeys keys, AppSecretProperties props) {
        this.keys = keys;
        this.kek = props.subkey("user-kek");
    }

    public UserKey create(UUID userId) {
        byte[] dek = new byte[32];
        RANDOM.nextBytes(dek);
        byte[] wrapped = AesGcm.encrypt(kek, dek, aad(userId));
        return keys.save(new UserKey(userId, wrapped, KEK_VERSION));
    }

    public byte[] unwrap(UUID userId) {
        UserKey k = keys.findById(userId).orElseThrow();
        if (k.isDestroyed()) throw new IllegalStateException("Chave destruída: dados deste usuário foram eliminados.");
        return AesGcm.decrypt(kek, k.getWrappedDek(), aad(userId));
    }

    public void shred(UUID userId, Instant now) {
        keys.findById(userId).ifPresent(k -> {
            k.destroy(now);
            keys.save(k);
        });
    }

    private static byte[] aad(UUID userId) {
        return ("user-dek:" + userId).getBytes(StandardCharsets.UTF_8);
    }
}
