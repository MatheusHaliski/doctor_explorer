package com.doctorexplorer.clinical.crypto;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.stereotype.Component;

/**
 * Assinatura eletrônica avançada da nota no MVP (RF08, critério 4): sessão do médico + sudo recente + carimbo de
 * tempo do servidor, selados por Ed25519 da clinical-api sobre o SHA-256 do conteúdo canônico. ICP-Brasil para a
 * nota está em ⚖️ validação com o Diretor-Técnico; receita e exame usam ICP-Brasil (RF09).
 */
@Component
public class NoteSigner {

    private final Ed25519PrivateKeyParameters privateKey;
    private final Ed25519PublicKeyParameters publicKey;
    private final String keyId;

    public NoteSigner(ClinicalProperties props) {
        this.privateKey = new Ed25519PrivateKeyParameters(props.subkey("composition-signing"), 0);
        this.publicKey = privateKey.generatePublicKey();
        this.keyId = "clinical-ed25519:" + sha256Hex(publicKey.getEncoded()).substring(0, 16);
    }

    public String keyId() {
        return keyId;
    }

    public String sign(String contentHash) {
        byte[] data = contentHash.getBytes(StandardCharsets.US_ASCII);
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, privateKey);
        s.update(data, 0, data.length);
        return Base64.getEncoder().encodeToString(s.generateSignature());
    }

    public boolean verify(String contentHash, String signature) {
        try {
            byte[] data = contentHash.getBytes(StandardCharsets.US_ASCII);
            Ed25519Signer v = new Ed25519Signer();
            v.init(false, publicKey);
            v.update(data, 0, data.length);
            return v.verifySignature(Base64.getDecoder().decode(signature));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
