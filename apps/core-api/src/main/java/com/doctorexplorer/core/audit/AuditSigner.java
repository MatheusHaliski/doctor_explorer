package com.doctorexplorer.core.audit;

import com.doctorexplorer.core.config.AppSecretProperties;
import java.util.Base64;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.stereotype.Component;

/**
 * Assina as exportações de auditoria com Ed25519. A chave privada deriva do segredo da aplicação
 * (HKDF, finalidade "audit-export-signing"); a pública é publicada em /admin/audit/public-key para o auditor
 * conferir com OpenSSL sem depender do Doctor Explorer.
 */
@Component
public class AuditSigner {

    /** Prefixo DER de SubjectPublicKeyInfo para Ed25519 (RFC 8410): SEQUENCE{ SEQUENCE{ OID 1.3.101.112 }, BIT STRING }. */
    private static final byte[] SPKI_PREFIX = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    private final Ed25519PrivateKeyParameters privateKey;
    private final Ed25519PublicKeyParameters publicKey;

    public AuditSigner(AppSecretProperties secret) {
        this.privateKey = new Ed25519PrivateKeyParameters(secret.subkey("audit-export-signing"), 0);
        this.publicKey = privateKey.generatePublicKey();
    }

    public String signBase64(byte[] data) {
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, privateKey);
        s.update(data, 0, data.length);
        return Base64.getEncoder().encodeToString(s.generateSignature());
    }

    public byte[] publicKeyDer() {
        byte[] raw = publicKey.getEncoded();
        byte[] der = new byte[SPKI_PREFIX.length + raw.length];
        System.arraycopy(SPKI_PREFIX, 0, der, 0, SPKI_PREFIX.length);
        System.arraycopy(raw, 0, der, SPKI_PREFIX.length, raw.length);
        return der;
    }

    public String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKeyDer()) + "\n-----END PUBLIC KEY-----\n";
    }
}
