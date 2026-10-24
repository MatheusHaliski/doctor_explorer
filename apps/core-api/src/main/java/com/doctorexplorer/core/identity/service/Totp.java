package com.doctorexplorer.core.identity.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** TOTP (RFC 6238) sobre HOTP (RFC 4226): HMAC-SHA1, 30 s, 6 dígitos, janela de ±1 passo. */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int STEP_SECONDS = 30;
    public static final int SECRET_BYTES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    public static byte[] newSecret() {
        byte[] s = new byte[SECRET_BYTES];
        RANDOM.nextBytes(s);
        return s;
    }

    public static String code(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = h[h.length - 1] & 0x0F;
            int binary = ((h[offset] & 0x7F) << 24) | ((h[offset + 1] & 0xFF) << 16) | ((h[offset + 2] & 0xFF) << 8) | (h[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String codeAt(byte[] secret, long epochSeconds) {
        return code(secret, epochSeconds / STEP_SECONDS);
    }

    /** Aceita o passo atual e os vizinhos (±1) para tolerar relógio fora de sincronia. */
    public static boolean verify(byte[] secret, String candidate, long epochSeconds) {
        if (candidate == null) return false;
        String c = candidate.replace(" ", "").trim();
        if (!c.matches("\\d{" + DIGITS + "}")) return false;
        long step = epochSeconds / STEP_SECONDS;
        boolean ok = false;
        for (long d = -1; d <= 1; d++) {
            ok |= constantTimeEquals(code(secret, step + d), c);
        }
        return ok;
    }

    public static String otpauthUri(byte[] secret, String issuer, String account) {
        return "otpauth://totp/" + url(issuer) + ":" + url(account) + "?secret=" + base32(secret)
                + "&issuer=" + url(issuer) + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    public static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
