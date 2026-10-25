package com.doctorexplorer.clinical.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Segredo próprio da clinical-api ({@code DE_CLINICAL_SECRET}, Base64 de 32+ bytes), separado do segredo da
 * core-api (ADR-001): dele derivam a chave mestra dos cofres, o índice cego e a chave de assinatura das notas.
 * Em produção a chave mestra vem do KMS. {@code core} diz onde validar o token interno e devolver a auditoria.
 */
@ConfigurationProperties("doctor-explorer")
public record ClinicalProperties(String clinicalSecret, @DefaultValue Core core) {

    public record Core(@DefaultValue("http://localhost:8080") String baseUrl, String publicKey, @DefaultValue("10s") Duration timeout) {}

    public boolean hasSecret() {
        return clinicalSecret != null && !clinicalSecret.isBlank();
    }

    public byte[] secretBytes() {
        byte[] bytes = Base64.getDecoder().decode(clinicalSecret.trim());
        if (bytes.length < 32) throw new IllegalStateException("DE_CLINICAL_SECRET precisa ter 32 bytes ou mais depois de decodificar o Base64.");
        return bytes;
    }

    /** HKDF-SHA256 (RFC 5869) com salt zero, como na core-api, mas com prefixo próprio. */
    public byte[] subkey(String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = mac.doFinal(secretBytes());
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(("doctor-explorer-clinical/" + purpose).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 1);
            return mac.doFinal();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
