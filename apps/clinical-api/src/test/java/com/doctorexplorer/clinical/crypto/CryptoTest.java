package com.doctorexplorer.clinical.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CryptoTest {

    static final ClinicalProperties PROPS = new ClinicalProperties("aXQtY2xpbmljYWwtc2VjcmV0LWl0LWNsaW5pY2FsLXNlY3JldC0xMjM0NTY=",
            new ClinicalProperties.Core("http://localhost", null, java.time.Duration.ofSeconds(1)));

    @Test
    void fieldEncryptionIsBoundToVaultKeyAndToTheRow() {
        VaultCrypto crypto = new VaultCrypto(PROPS);
        UUID vault = UUID.randomUUID();
        byte[] dek = crypto.unwrapDek(vault, crypto.newWrappedDek(vault));
        UUID row = UUID.randomUUID();
        byte[] sealed = crypto.encrypt(dek, "patient.name", row, "Maria da Silva");

        assertThat(new String(sealed, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Maria");
        assertThat(crypto.decrypt(dek, "patient.name", row, sealed)).isEqualTo("Maria da Silva");
        assertThatThrownBy(() -> crypto.decrypt(dek, "patient.name", UUID.randomUUID(), sealed)).as("texto cifrado copiado para outra linha").isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(dek, "patient.cpf", row, sealed)).as("outra coluna").isInstanceOf(IllegalStateException.class);
        byte[] wrapped = crypto.newWrappedDek(vault);
        assertThatThrownBy(() -> crypto.unwrapDek(UUID.randomUUID(), wrapped)).as("DEK embrulhada para outro cofre").isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blindIndexMatchesPrefixesWithoutAccentsAndDiffersPerVault() {
        BlindIndex index = new BlindIndex(PROPS);
        UUID vault = UUID.randomUUID();
        var stored = index.tokensFor(vault, "Maria Conceição da Silva");
        for (String q : new String[] {"mar", "CONCEI", "silva", "maria silva", "conceicao"}) {
            assertThat(stored).as(q).containsAll(index.queryTokens(vault, q));
        }
        assertThat(stored).doesNotContainAnyElementsOf(index.queryTokens(vault, "joana"));
        assertThat(stored).as("mesmo nome em outro cofre gera outro token").doesNotContainAnyElementsOf(index.queryTokens(UUID.randomUUID(), "maria"));
    }

    @Test
    void noteSignatureVerifiesOnlyTheSignedHash() {
        NoteSigner signer = new NoteSigner(PROPS);
        String hash = NoteSigner.sha256Hex("conteúdo".getBytes());
        String sig = signer.sign(hash);
        assertThat(signer.verify(hash, sig)).isTrue();
        assertThat(signer.verify(NoteSigner.sha256Hex("outro".getBytes()), sig)).isFalse();
        assertThat(signer.keyId()).startsWith("clinical-ed25519:");
    }
}
