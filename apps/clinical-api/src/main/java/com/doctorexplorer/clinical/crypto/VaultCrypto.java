package com.doctorexplorer.clinical.crypto;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Envelope por cofre: cada cofre tem uma chave de dados (DEK, AES-256) embrulhada pela chave mestra (KEK). No MVP a
 * KEK deriva de {@code DE_CLINICAL_SECRET}; em produção o embrulho é feito pelo KMS (ADR-011) e só a DEK
 * desembrulhada passa pela memória. Cada campo leva como dado associado (AAD) "tabela.coluna:id", então copiar o
 * texto cifrado de uma linha para outra não decifra.
 */
@Component
public class VaultCrypto {

    public static final String KEY_ID = "local-hkdf-v1";
    private static final byte VERSION = 1;
    private static final int IV = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] kek;

    public VaultCrypto(ClinicalProperties props) {
        this.kek = props.subkey("vault-kek");
    }

    public byte[] newWrappedDek(UUID vaultId) {
        byte[] dek = new byte[32];
        RANDOM.nextBytes(dek);
        return seal(kek, dek, ("vault:" + vaultId).getBytes(StandardCharsets.UTF_8));
    }

    public byte[] unwrapDek(UUID vaultId, byte[] wrapped) {
        return open(kek, wrapped, ("vault:" + vaultId).getBytes(StandardCharsets.UTF_8));
    }

    public byte[] encrypt(byte[] dek, String field, UUID rowId, String plaintext) {
        if (plaintext == null) return null;
        return seal(dek, plaintext.getBytes(StandardCharsets.UTF_8), aad(field, rowId));
    }

    public String decrypt(byte[] dek, String field, UUID rowId, byte[] sealed) {
        if (sealed == null) return null;
        return new String(open(dek, sealed, aad(field, rowId)), StandardCharsets.UTF_8);
    }

    private static byte[] aad(String field, UUID rowId) {
        return (field + ":" + rowId).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] seal(byte[] key, byte[] plain, byte[] aad) {
        try {
            byte[] iv = new byte[IV];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            c.updateAAD(aad);
            byte[] ct = c.doFinal(plain);
            return ByteBuffer.allocate(1 + IV + ct.length).put(VERSION).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] open(byte[] key, byte[] sealed, byte[] aad) {
        try {
            if (sealed.length < 1 + IV + 16 || sealed[0] != VERSION) throw new IllegalStateException("formato de cifra desconhecido");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, sealed, 1, IV));
            c.updateAAD(aad);
            return c.doFinal(sealed, 1 + IV, sealed.length - 1 - IV);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("não foi possível decifrar o campo (chave ou dado associado não confere)", e);
        }
    }
}
