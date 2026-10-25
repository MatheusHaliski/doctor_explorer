package com.doctorexplorer.clinical.signature;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Padrão de produção enquanto o contrato com um provedor homologado pelo ITI não estiver configurado: falha fechada
 * com causa clara, sem nunca assinar com chave nossa.
 */
public class UnavailableSignatureProvider implements SignatureProviderPort {

    @Override
    public String name() {
        return "NENHUM";
    }

    @Override
    public SignedCms sign(SignerIdentity who, byte[] signedContent) {
        throw new SignatureFailure(SignatureFailure.Cause.PROVEDOR_INDISPONIVEL,
                "Nenhum provedor de assinatura ICP-Brasil está configurado. O rascunho foi mantido; tente de novo quando o provedor voltar.");
    }

    @Override
    public List<X509Certificate> trustAnchors() {
        return List.of();
    }
}
