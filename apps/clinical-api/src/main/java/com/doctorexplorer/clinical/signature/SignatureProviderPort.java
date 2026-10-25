package com.doctorexplorer.clinical.signature;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * ADR-007 · provedor de assinatura ICP-Brasil em nuvem (certificado A3 no HSM do provedor; o médico autoriza no app
 * dele). Recebe os bytes cobertos pelo /ByteRange do PDF e devolve o CMS destacado (CAdES, com certificado do
 * signatário e carimbo do tempo), pronto para virar a assinatura PAdES. O adaptador de um provedor real calcula o
 * hash e envia só o hash.
 */
public interface SignatureProviderPort {

    record SignedCms(byte[] cms, X509Certificate signer) {}

    /** Nome curto que vai no documento e na validação ("SIMULADO" fora de produção). */
    String name();

    SignedCms sign(SignerIdentity who, byte[] signedContent) throws SignatureFailure;

    /** Âncoras de confiança para conferir a cadeia (em produção, as AC raiz da ICP-Brasil). */
    List<X509Certificate> trustAnchors();
}
