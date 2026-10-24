package com.doctorexplorer.core.identity.domain;

public class InvalidTransitionException extends IllegalStateException {

    private final Enum<?> from;
    private final Enum<?> to;

    public InvalidTransitionException(Enum<?> from, Enum<?> to) {
        super("Transição inválida: " + from + " → " + to);
        this.from = from;
        this.to = to;
    }

    public Enum<?> from() {
        return from;
    }

    public Enum<?> to() {
        return to;
    }
}
