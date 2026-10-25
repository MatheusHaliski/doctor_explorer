package com.doctorexplorer.clinical.web;

import java.util.List;

/** Vira 422 com todos os problemas juntos. */
public class ValidationProblemException extends RuntimeException {

    public record FieldProblem(String field, String message) {}

    private final List<FieldProblem> problems;

    public ValidationProblemException(List<FieldProblem> problems) {
        super("Dados inválidos: " + problems.size() + " problema(s)");
        this.problems = List.copyOf(problems);
    }

    public List<FieldProblem> problems() {
        return problems;
    }
}
