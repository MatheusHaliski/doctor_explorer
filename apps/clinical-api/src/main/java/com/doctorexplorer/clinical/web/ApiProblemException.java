package com.doctorexplorer.clinical.web;

import java.net.URI;
import org.springframework.http.HttpStatus;

/** Erro de negócio com status, título e detalhe em pt-BR; vira Problem Details. */
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final URI type;

    public ApiProblemException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = URI.create("https://doctorexplorer.app/problemas/" + type);
        this.title = title;
    }

    public HttpStatus status() { return status; }
    public String title() { return title; }
    public URI type() { return type; }

    public static ApiProblemException notFound(String what) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", what + " não existe ou não está no seu cofre.");
    }
}
