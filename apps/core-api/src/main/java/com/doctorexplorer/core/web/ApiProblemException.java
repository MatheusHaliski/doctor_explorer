package com.doctorexplorer.core.web;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erro de negócio com status, título e detalhe em pt-BR; vira Problem Details. */
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final URI type;
    private final Map<String, Object> properties;

    public ApiProblemException(HttpStatus status, String type, String title, String detail) {
        this(status, type, title, detail, Map.of());
    }

    public ApiProblemException(HttpStatus status, String type, String title, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.type = URI.create("https://doctorexplorer.app/problemas/" + type);
        this.title = title;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() { return status; }
    public String title() { return title; }
    public URI type() { return type; }
    public Map<String, Object> properties() { return properties; }
}
