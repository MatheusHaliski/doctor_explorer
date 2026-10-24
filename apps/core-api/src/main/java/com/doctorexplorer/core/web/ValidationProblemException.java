package com.doctorexplorer.core.web;

import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import java.util.List;

/** Vira 422 com todos os problemas juntos (Bean Validation + regras de domínio). */
public class ValidationProblemException extends RuntimeException {

    private final List<FieldProblem> problems;

    public ValidationProblemException(List<FieldProblem> problems) {
        super("Dados inválidos: " + problems.size() + " problema(s)");
        this.problems = List.copyOf(problems);
    }

    public List<FieldProblem> problems() {
        return problems;
    }
}
