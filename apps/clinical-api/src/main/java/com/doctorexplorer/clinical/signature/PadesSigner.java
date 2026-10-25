package com.doctorexplorer.clinical.signature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.springframework.stereotype.Component;

/**
 * PAdES: atualização incremental do PDF/A com um campo de assinatura invisível (ETSI.CAdES.detached). O provedor
 * assina os bytes do /ByteRange; o CMS devolvido (com carimbo do tempo) vai para o /Contents.
 */
@Component
public class PadesSigner {

    private final SignatureProviderPort provider;

    public PadesSigner(SignatureProviderPort provider) {
        this.provider = provider;
    }

    public record Signed(byte[] pdf, String certSubject, String certSerial) {}

    public Signed sign(byte[] pdf, SignerIdentity who, String reason, Instant when) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            sig.setName(who.name());
            sig.setLocation("Brasil");
            sig.setReason(reason);
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(when.toEpochMilli());
            sig.setSignDate(cal);
            SignatureOptions opts = new SignatureOptions();
            opts.setPreferredSignatureSize(32_768);
            doc.addSignature(sig, opts);
            // PDF/A: toda anotação precisa estar marcada para impressão, inclusive o campo de assinatura invisível
            for (PDSignatureField f : doc.getSignatureFields()) {
                for (PDAnnotationWidget w : f.getWidgets()) w.setPrinted(true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ExternalSigningSupport ext = doc.saveIncrementalForExternalSigning(out);
            byte[] content = ext.getContent().readAllBytes();
            SignatureProviderPort.SignedCms cms = provider.sign(who, content);
            ext.setSignature(cms.cms());
            opts.close();
            return new Signed(out.toByteArray(), cms.signer().getSubjectX500Principal().getName(), cms.signer().getSerialNumber().toString(16));
        } catch (IOException e) {
            throw new IllegalStateException("falha ao montar o PDF assinado", e);
        }
    }
}
