package com.doctorexplorer.core.common;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM com nonce de 12 bytes prefixado ao resultado. Usado para envelopar a DEK e cifrar o segredo TOTP. */
public final class AesGcm {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE = 12;
    private static final int TAG_BITS = 128;

    private AesGcm() {}

    public static byte[] encrypt(byte[] key, byte[] plain, byte[] aad) {
        try {
            byte[] nonce = new byte[NONCE];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            if (aad != null) cipher.updateAAD(aad);
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[NONCE + ct.length];
            System.arraycopy(nonce, 0, out, 0, NONCE);
            System.arraycopy(ct, 0, out, NONCE, ct.length);
            return out;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] blob, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, blob, 0, NONCE));
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(blob, NONCE, blob.length - NONCE);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
