package com.doctorexplorer.clinical.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/** Emite tokens internos no mesmo formato da core-api (v1.payload.Ed25519), com chave de teste. */
public final class TestTokens {

    public static final KeyPair KEYS;

    static {
        try {
            KEYS = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TestTokens() {}

    public static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    }

    public record Doctor(UUID id, String name, String crm, String uf) {
        public static Doctor random(String name) {
            return new Doctor(UUID.randomUUID(), name, String.valueOf(100000 + (int) (Math.random() * 800000)), "PR");
        }
    }

    public static String token(Doctor d, boolean write, boolean sudo, String seal) {
        return token(d, write, sudo, seal, "clinical-api", System.currentTimeMillis() + 60_000);
    }

    public static String token(Doctor d, boolean write, boolean sudo, String seal, String aud, long exp) {
        long now = System.currentTimeMillis();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("v", 1);
        c.put("iss", "core-api");
        c.put("aud", aud);
        c.put("sub", d.id().toString());
        c.put("name", d.name());
        c.put("crm", d.crm());
        c.put("uf", d.uf());
        c.put("seal", seal);
        c.put("sudoUntil", sudo ? now + 600_000 : 0);
        c.put("mfa", false);
        c.put("write", write);
        c.put("iat", now);
        c.put("exp", exp);
        c.put("jti", UUID.randomUUID().toString());
        try {
            String body = "v1." + b64(new ObjectMapper().writeValueAsBytes(c));
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(KEYS.getPrivate());
            s.update(body.getBytes(StandardCharsets.US_ASCII));
            return body + "." + b64(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
