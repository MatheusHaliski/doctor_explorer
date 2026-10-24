package com.doctorexplorer.core.web;

import com.doctorexplorer.core.identity.domain.InvalidTransitionException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Problem Details (RFC 9457) com título e detalhe em pt-BR. Validação devolve 422 com {@code errors[]}
 * contendo todos os problemas de uma vez (heurística 9: diga o que está errado e como corrigir).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final URI TYPE_VALIDATION = URI.create("https://doctorexplorer.app/problemas/validacao");
    public static final URI TYPE_STATE = URI.create("https://doctorexplorer.app/problemas/estado-invalido");

    public record FieldProblem(String field, String message) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldProblem(e.getField(), messageOf(e)))
                .toList();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                errors.size() == 1 ? "Há 1 campo para corrigir." : "Há " + errors.size() + " campos para corrigir.");
        pd.setTitle("Dados inválidos");
        pd.setType(TYPE_VALIDATION);
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).headers(headers).body(pd);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidTransitionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A conta não pode passar de " + ex.from() + " para " + ex.to() + ". Recarregue a página e tente de novo.");
        pd.setTitle("Estado inválido");
        pd.setType(TYPE_STATE);
        return pd;
    }

    private static String messageOf(FieldError e) {
        String m = e.getDefaultMessage();
        return m == null || m.isBlank() ? "Valor inválido." : m;
    }
}
