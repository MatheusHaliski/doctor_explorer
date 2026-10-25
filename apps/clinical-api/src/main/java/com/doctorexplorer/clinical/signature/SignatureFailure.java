package com.doctorexplorer.clinical.signature;

/** Falha do provedor com causa e ação para o médico (heurística 9). Não é erro de programa: vira FALHA_ASSINATURA. */
public class SignatureFailure extends RuntimeException {

    public enum Cause { PROVEDOR_INDISPONIVEL, CERTIFICADO_INVALIDO, NAO_AUTORIZADO }

    private final Cause cause;

    public SignatureFailure(Cause cause, String message) {
        super(message);
        this.cause = cause;
    }

    public Cause failureCause() {
        return cause;
    }
}
