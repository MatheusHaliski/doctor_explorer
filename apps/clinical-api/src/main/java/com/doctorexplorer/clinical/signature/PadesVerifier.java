package com.doctorexplorer.clinical.signature;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TimeStampToken;
import org.springframework.stereotype.Component;

/**
 * Confere a assinatura PAdES de um PDF: o /ByteRange cobre o arquivo inteiro (nada foi acrescentado depois), o CMS
 * confere com os bytes, o certificado encadeia numa âncora de confiança do provedor e estava válido na hora do
 * carimbo do tempo, e o carimbo confere com a assinatura. Usado pelo validador público e na hora de baixar.
 */
@Component
public class PadesVerifier {

    public record Check(boolean valid, String level, String signerName, String crm, Instant signedAt, String issuer, List<String> problems) {}

    private final SignatureProviderPort provider;

    public PadesVerifier(SignatureProviderPort provider) {
        this.provider = provider;
    }

    public Check verify(byte[] pdf) {
        List<String> problems = new ArrayList<>();
        String signer = null;
        String crm = null;
        Instant at = null;
        String issuer = null;
        String level = "nenhuma";
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<PDSignature> sigs = doc.getSignatureDictionaries();
            if (sigs.size() != 1) return new Check(false, level, null, null, null, null, List.of("O documento deveria ter exatamente uma assinatura."));
            PDSignature s = sigs.get(0);
            int[] br = s.getByteRange();
            if (br.length != 4 || br[0] != 0 || (long) br[2] + br[3] != pdf.length) problems.add("Há conteúdo fora da área assinada (o arquivo mudou depois da assinatura).");
            // o /Contents vem completado com zeros até o tamanho reservado: lê só o primeiro objeto DER
            ContentInfo info;
            try (ASN1InputStream in = new ASN1InputStream(s.getContents(pdf))) {
                info = ContentInfo.getInstance(in.readObject());
            }
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(s.getSignedContent(pdf)), info);
            SignerInformation si = cms.getSignerInfos().getSigners().iterator().next();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = cms.getCertificates().getMatches(si.getSID());
            if (matches.isEmpty()) return new Check(false, level, null, null, null, null, List.of("O certificado do signatário não veio na assinatura."));
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(matches.iterator().next());
            signer = cn(cert);
            crm = crmOf(cert);
            issuer = cert.getIssuerX500Principal().getName();
            if (!si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert))) problems.add("A assinatura não confere com o conteúdo.");
            level = "PAdES-B-B";
            if (si.getSignedAttributes() == null || si.getSignedAttributes().get(PKCSObjectIdentifiers.id_aa_signingCertificateV2) == null) {
                problems.add("Falta o atributo signing-certificate-v2 (exigido no PAdES).");
            }
            if (!chainsToAnchor(cert)) problems.add("O certificado não é de uma autoridade confiável para este ambiente.");

            Attribute tsAttr = si.getUnsignedAttributes() == null ? null : si.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
            if (tsAttr == null) {
                problems.add("Sem carimbo do tempo.");
            } else {
                TimeStampToken tst = new TimeStampToken(ContentInfo.getInstance(tsAttr.getAttrValues().getObjectAt(0)));
                X509Certificate tsaCert = null;
                @SuppressWarnings("unchecked")
                Collection<X509CertificateHolder> tsaCerts = tst.getCertificates().getMatches(null);
                for (X509CertificateHolder h : tsaCerts) {
                    if (tst.isSignatureValid(new JcaSimpleSignerInfoVerifierBuilder().build(h))) {
                        tsaCert = new JcaX509CertificateConverter().getCertificate(h);
                        break;
                    }
                }
                if (tsaCert == null || !chainsToAnchor(tsaCert)) {
                    problems.add("O carimbo do tempo não é de uma autoridade confiável.");
                } else {
                    tst.validate(new JcaSimpleSignerInfoVerifierBuilder().build(tsaCert));
                    byte[] imprint = MessageDigest.getInstance("SHA-256").digest(si.getSignature());
                    if (!Arrays.equals(imprint, tst.getTimeStampInfo().getMessageImprintDigest())) problems.add("O carimbo do tempo é de outra assinatura.");
                    at = tst.getTimeStampInfo().getGenTime().toInstant();
                    try {
                        cert.checkValidity(java.util.Date.from(at));
                    } catch (Exception e) {
                        problems.add("O certificado não estava válido na hora da assinatura.");
                    }
                    level = "PAdES-B-T";
                }
            }
        } catch (Exception e) {
            problems.add("Não foi possível ler a assinatura: " + e.getClass().getSimpleName());
        }
        return new Check(problems.isEmpty(), level, signer, crm, at, issuer, problems);
    }

    private boolean chainsToAnchor(X509Certificate cert) {
        for (X509Certificate anchor : provider.trustAnchors()) {
            if (!cert.getIssuerX500Principal().equals(anchor.getSubjectX500Principal())) continue;
            try {
                cert.verify(anchor.getPublicKey());
                return true;
            } catch (Exception e) {
                // tenta a próxima âncora
            }
        }
        return false;
    }

    private static String cn(X509Certificate cert) {
        for (String part : cert.getSubjectX500Principal().getName().split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            if (part.startsWith("CN=")) return part.substring(3).replace("\\", "");
        }
        return cert.getSubjectX500Principal().getName();
    }

    /** CRM+UF do otherName 2.16.76.1.4.2.1.1 (DOC-ICP-04). */
    static String crmOf(X509Certificate cert) {
        try {
            byte[] ext = cert.getExtensionValue(Extension.subjectAlternativeName.getId());
            if (ext == null) return null;
            GeneralNames names = GeneralNames.getInstance(ASN1OctetString.getInstance(ext).getOctets());
            for (GeneralName n : names.getNames()) {
                if (n.getTagNo() != GeneralName.otherName) continue;
                ASN1Sequence seq = ASN1Sequence.getInstance(n.getName());
                if (!SimulatedIcpProvider.CRM_OID.equals(seq.getObjectAt(0).toString())) continue;
                ASN1TaggedObject t = ASN1TaggedObject.getInstance(seq.getObjectAt(1));
                return ((ASN1String) t.getExplicitBaseObject()).getString();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

}
