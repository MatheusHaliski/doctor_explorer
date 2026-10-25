package com.doctorexplorer.core.media.pipeline;

/** O arquivo não serve (formato, tamanho, pixels): estado REJEITADO com o motivo em pt-BR. */
public class MediaRejected extends RuntimeException {
    public MediaRejected(String reason) {
        super(reason);
    }
}
