package com.doctorexplorer.clinical.signature;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

/**
 * Provedor ICP-Brasil SIMULADO para dev e testes. Tem a forma do real (certificado do médico emitido por uma AC, com
 * o CRM no OID 2.16.76.1.4.2.1.1 do DOC-ICP-04; CAdES destacado com signing-certificate-v2 e carimbo do tempo de uma
 * TSA → PAdES-B-T), mas a AC é de teste e todos os nomes dizem "SIMULADA · sem validade jurídica". As chaves (ECDSA
 * P-256) derivam de {@code DE_CLINICAL_SECRET}, então a cadeia continua conferindo depois de reiniciar.
 */
public class SimulatedIcpProvider implements SignatureProviderPort {

    public static final String CRM_OID = "2.16.76.1.4.2.1.1";
    static final ASN1ObjectIdentifier TSA_POLICY = new ASN1ObjectIdentifier("2.25.318543027471837391205361917519356842611");
    private static final String CURVE = "secp256r1";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
    }

    private final ClinicalProperties props;
    private final KeyPair rootKeys;
    private final X509Certificate root;
    private final KeyPair tsaKeys;
    private final X509Certificate tsa;
    private final Map<String, Issued> signers = new ConcurrentHashMap<>();

    private record Issued(KeyPair keys, X509Certificate cert) {}

    public SimulatedIcpProvider(ClinicalProperties props) {
        this.props = props;
        try {
            rootKeys = derive("icp-sim-root");
            X500Name rootName = name("AC Raiz SIMULADA Doctor Explorer", "Sem validade juridica");
            root = cert(rootName, rootName, rootKeys.getPublic(), rootKeys, serial("root"), LocalDate.of(2026, 1, 1), LocalDate.of(2036, 1, 1), true, null);
            tsaKeys = derive("icp-sim-tsa");
            tsa = cert(rootName, name("Carimbo do Tempo SIMULADO Doctor Explorer", "Sem validade juridica"),
                    tsaKeys.getPublic(), rootKeys, serial("tsa"), LocalDate.of(2026, 1, 1), LocalDate.of(2036, 1, 1), false, KeyPurposeId.id_kp_timeStamping);
        } catch (Exception e) {
            throw new IllegalStateException("AC simulada não iniciou", e);
        }
    }

    @Override
    public String name() {
        return "SIMULADO";
    }

    @Override
    public List<X509Certificate> trustAnchors() {
        return List.of(root);
    }

    public X509Certificate tsaCertificate() {
        return tsa;
    }

    @Override
    public SignedCms sign(SignerIdentity who, byte[] signedContent) {
        if (who.crm() == null || who.crm().isBlank() || who.uf() == null || who.uf().isBlank()) {
            throw new SignatureFailure(SignatureFailure.Cause.CERTIFICADO_INVALIDO, "O certificado precisa do CRM e da UF do médico; confira o selo do CRM.");
        }
        try {
            Issued me = signers.computeIfAbsent(who.userId() + "|" + who.crm() + who.uf() + "|" + who.name() + "|" + LocalDate.now(ZoneOffset.UTC).getYear(),
                    k -> issue(who));
            ContentSigner cs = new JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(me.keys().getPrivate());
            byte[] certHash = MessageDigest.getInstance("SHA-256").digest(me.cert().getEncoded());
            SigningCertificateV2 scv2 = new SigningCertificateV2(new ESSCertIDv2[] {new ESSCertIDv2(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256), certHash)});
            AttributeTable extra = new AttributeTable(new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new DERSet(scv2)));
            // PAdES baseline: o horário vai no /M do PDF e no carimbo do tempo, nunca no atributo signing-time do CMS
            CMSAttributeTableGenerator signedAttrs = new DefaultSignedAttributeTableGenerator(extra) {
                @Override
                public AttributeTable getAttributes(@SuppressWarnings("rawtypes") Map params) {
                    return super.getAttributes(params).remove(CMSAttributes.signingTime);
                }
            };
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                    .setSignedAttributeGenerator(signedAttrs).build(cs, me.cert()));
            gen.addCertificates(new JcaCertStore(List.of(me.cert(), root)));
            CMSSignedData sd = gen.generate(new CMSProcessableByteArray(signedContent), false);

            SignerInformation si = sd.getSignerInfos().getSigners().iterator().next();
            TimeStampToken token = timestamp(MessageDigest.getInstance("SHA-256").digest(si.getSignature()));
            Attribute ts = new Attribute(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, new DERSet(ASN1Primitive.fromByteArray(token.getEncoded())));
            si = SignerInformation.replaceUnsignedAttributes(si, new AttributeTable(ts));
            sd = CMSSignedData.replaceSigners(sd, new SignerInformationStore(si));
            return new SignedCms(sd.getEncoded("DER"), me.cert());
        } catch (SignatureFailure e) {
            throw e;
        } catch (Exception e) {
            throw new SignatureFailure(SignatureFailure.Cause.PROVEDOR_INDISPONIVEL, "O provedor de assinatura falhou: " + e.getClass().getSimpleName());
        }
    }

    TimeStampToken timestamp(byte[] imprint) throws Exception {
        TimeStampTokenGenerator gen = new TimeStampTokenGenerator(
                new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC").build("SHA256withECDSA", tsaKeys.getPrivate(), tsa),
                new JcaDigestCalculatorProviderBuilder().build().get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)), TSA_POLICY);
        gen.addCertificates(new JcaCertStore(List.of(tsa)));
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        reqGen.setCertReq(true); // o certificado da TSA vai junto no carimbo, para conferir sem consulta externa
        TimeStampRequest req = reqGen.generate(TSPAlgorithms.SHA256, imprint, BigInteger.valueOf(System.nanoTime()));
        return gen.generate(req, new BigInteger(64, new java.security.SecureRandom()), new Date());
    }

    private Issued issue(SignerIdentity who) {
        try {
            KeyPair keys = derive("icp-sim-signer:" + who.userId());
            int year = LocalDate.now(ZoneOffset.UTC).getYear();
            X500Name subject = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.C, "BR").addRDN(BCStyle.O, "ICP-Brasil SIMULADA")
                    .addRDN(BCStyle.OU, "Certificado SIMULADO - sem validade juridica").addRDN(BCStyle.OU, "CRM " + who.crm() + "/" + who.uf())
                    .addRDN(BCStyle.CN, who.name()).build();
            X509Certificate cert = cert(X500Name.getInstance(root.getSubjectX500Principal().getEncoded()), subject, keys.getPublic(), rootKeys,
                    serial("signer:" + who.userId() + ":" + year), LocalDate.of(year, 1, 1), LocalDate.of(year + 2, 1, 1), false, null, who.crm() + who.uf());
            return new Issued(keys, cert);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private X509Certificate cert(X500Name issuer, X500Name subject, java.security.PublicKey pub, KeyPair issuerKeys, BigInteger serial, LocalDate from,
            LocalDate to, boolean ca, KeyPurposeId eku) throws Exception {
        return cert(issuer, subject, pub, issuerKeys, serial, from, to, ca, eku, null);
    }

    private X509Certificate cert(X500Name issuer, X500Name subject, java.security.PublicKey pub, KeyPair issuerKeys, BigInteger serial, LocalDate from,
            LocalDate to, boolean ca, KeyPurposeId eku, String crmUf) throws Exception {
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(issuer, serial, Date.from(from.atStartOfDay().toInstant(ZoneOffset.UTC)),
                Date.from(to.atStartOfDay().toInstant(ZoneOffset.UTC)), subject, pub);
        JcaX509ExtensionUtils u = new JcaX509ExtensionUtils();
        b.addExtension(Extension.subjectKeyIdentifier, false, u.createSubjectKeyIdentifier(pub));
        b.addExtension(Extension.authorityKeyIdentifier, false, u.createAuthorityKeyIdentifier(issuerKeys.getPublic()));
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        if (ca) {
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } else {
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        }
        if (eku != null) b.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(eku));
        if (crmUf != null) {
            // DOC-ICP-04: CRM (número + UF) num otherName do SubjectAltName
            ASN1Encodable crm = new DERSequence(new ASN1Encodable[] {new ASN1ObjectIdentifier(CRM_OID), new DERTaggedObject(true, 0, new DERPrintableString(crmUf))});
            b.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.otherName, crm)));
        }
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(issuerKeys.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(b.build(signer));
    }

    private KeyPair derive(String purpose) throws Exception {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(CURVE);
        BigInteger n = spec.getN();
        BigInteger d = new BigInteger(1, props.subkey(purpose)).mod(n.subtract(BigInteger.ONE)).add(BigInteger.ONE);
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        return new KeyPair(kf.generatePublic(new ECPublicKeySpec(spec.getG().multiply(d).normalize(), spec)), kf.generatePrivate(new ECPrivateKeySpec(d, spec)));
    }

    private static BigInteger serial(String seed) throws Exception {
        return new BigInteger(1, java.util.Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)), 16));
    }

    private static X500Name name(String cn, String ou) {
        return new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.C, "BR").addRDN(BCStyle.O, "ICP-Brasil SIMULADA").addRDN(BCStyle.OU, ou)
                .addRDN(BCStyle.CN, cn).build();
    }
}
