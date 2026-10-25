package com.doctorexplorer.clinical.web;

import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Problem Details (RFC 9457) em pt-BR, no mesmo formato da core-api (o web mostra os dois igual). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ValidationProblemException.class)
    ResponseEntity<ProblemDetail> validation(ValidationProblemException ex) {
        int n = ex.problems().size();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, n == 1 ? "Há 1 campo para corrigir." : "Há " + n + " campos para corrigir.");
        pd.setTitle("Dados inválidos");
        pd.setType(URI.create("https://doctorexplorer.app/problemas/validacao"));
        pd.setProperty("errors", ex.problems());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(pd);
    }

    @ExceptionHandler(ApiProblemException.class)
    ResponseEntity<ProblemDetail> problem(ApiProblemException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        pd.setTitle(ex.title());
        pd.setType(ex.type());
        return ResponseEntity.status(ex.status()).body(pd);
    }

    /** O gatilho do banco recusou alterar nota assinada: é conflito, não erro interno. */
    @ExceptionHandler({DataIntegrityViolationException.class, JpaSystemException.class})
    ResponseEntity<ProblemDetail> integrity(RuntimeException ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        boolean immutable = String.valueOf(t.getMessage()).contains("imutável");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, immutable
                ? "A nota assinada não pode ser alterada. Crie uma nova versão com o motivo da correção."
                : "A operação conflita com o estado atual. Recarregue e tente de novo.");
        pd.setTitle(immutable ? "Nota assinada é imutável" : "Conflito");
        pd.setType(URI.create("https://doctorexplorer.app/problemas/" + (immutable ? "nota-imutavel" : "conflito")));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }
}
