package com.doctorexplorer.clinical.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import com.doctorexplorer.clinical.documents.DocumentPdf;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RF09 · PDF/A-1b gerado, assinado em PAdES-B-T pelo provedor simulado e conferido por três validadores. */
class PadesPipelineTest {

    static final ClinicalProperties PROPS = new ClinicalProperties("aXQtY2xpbmljYWwtc2VjcmV0LWl0LWNsaW5pY2FsLXNlY3JldC0xMjM0NTY=",
            new ClinicalProperties.Core("http://localhost", null, Duration.ofSeconds(1)));

    final SimulatedIcpProvider provider = new SimulatedIcpProvider(PROPS);
    final DocumentPdf renderer = new DocumentPdf();
    final PadesSigner signer = new PadesSigner(provider);
    final PadesVerifier verifier = new PadesVerifier(provider);
    final SignerIdentity ana = new SignerIdentity(UUID.randomUUID(), "Dra. Ana Ribeiro", "123456", "PR");

    byte[] pdf() {
        return renderer.render(new DocumentPdf.Model("RECEITUÁRIO", "Dra. Ana Ribeiro", "123456", "PR", "Helena Duarte", LocalDate.of(1964, 3, 2),
                Instant.now(), List.of(new DocumentPdf.Item("AMOXICILINA · 500 MG CAP DURA CT BL AL PLAS TRANS X 21 (AMOXIL)",
                        List.of("Tomar 1 cápsula por via oral de 8 em 8 horas por 7 dias.", "Quantidade: 1 caixa"))),
                null, "Retorno em 7 dias.", "ABCD-EFGH-JKMN-PQRS", "http://localhost:3000/validar/ABCD-EFGH-JKMN-PQRS",
                List.of("Antimicrobiano: receita em 2 vias, a farmácia retém a segunda (RDC 471/2021)."), true));
    }

    @Test
    void renderedDocumentIsPdfA1b() throws Exception {
        assertThat(preflight(pdf())).isEmpty();
    }

    @Test
    void signedDocumentVerifiesAndStaysPdfA() throws Exception {
        byte[] signed = signer.sign(pdf(), ana, "Prescrição eletrônica", Instant.now()).pdf();
        PadesVerifier.Check c = verifier.verify(signed);
        assertThat(c.problems()).isEmpty();
        assertThat(c.valid()).isTrue();
        assertThat(c.level()).isEqualTo("PAdES-B-T");
        assertThat(c.signerName()).isEqualTo("Dra. Ana Ribeiro");
        assertThat(c.crm()).isEqualTo("123456PR");
        assertThat(c.signedAt()).isNotNull();
        assertThat(preflight(signed)).as("a atualização incremental mantém o PDF/A").isEmpty();
    }

    @Test
    void anyByteChangedOrAppendedBreaksTheSignature() {
        byte[] signed = signer.sign(pdf(), ana, "Prescrição eletrônica", Instant.now()).pdf();
        byte[] appended = new byte[signed.length + 10];
        System.arraycopy(signed, 0, appended, 0, signed.length);
        assertThat(verifier.verify(appended).valid()).isFalse();

        String s = new String(signed, StandardCharsets.ISO_8859_1);
        int i = s.indexOf("Doctor Explorer");
        byte[] tampered = signed.clone();
        tampered[i] = 'X';
        assertThat(verifier.verify(tampered).valid()).isFalse();

        // outra AC (mesmo formato, outro segredo) não é confiável aqui
        SimulatedIcpProvider other = new SimulatedIcpProvider(new ClinicalProperties("ZGV2LWNsaW5pY2FsLXNlY3JldC1kZXYtY2xpbmljYWwtc2VjcmV0LTEyMw==", PROPS.core()));
        byte[] foreign = new PadesSigner(other).sign(pdf(), ana, "x", Instant.now()).pdf();
        assertThat(verifier.verify(foreign).problems()).anyMatch(p -> p.contains("autoridade confiável"));
    }

    @Test
    void independentPopplerCheckAgreesTheSignatureIsValid(@TempDir Path dir) throws Exception {
        assumeTrue(new File("/usr/bin/pdfsig").canExecute(), "pdfsig ausente");
        Path f = dir.resolve("receita.pdf");
        Files.write(f, signer.sign(pdf(), ana, "Prescrição eletrônica", Instant.now()).pdf());
        Process p = new ProcessBuilder("pdfsig", f.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        assertThat(out).contains("Signature Validation: Signature is Valid").contains("ETSI.CAdES.detached").contains("Total document signed");
    }

    static List<String> preflight(byte[] pdf) throws Exception {
        File f = File.createTempFile("pdfa", ".pdf");
        try {
            Files.write(f.toPath(), pdf);
            ValidationResult r = PreflightParser.validate(f);
            return r.getErrorsList().stream().map(e -> e.getErrorCode() + " " + e.getDetails()).toList();
        } finally {
            f.delete();
        }
    }
}
