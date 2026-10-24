package com.doctorexplorer.core.identity.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Política de senha: frase de 12 a 128 caracteres, fora da lista de senhas comuns, sem conter o e-mail,
 * a parte local do e-mail nem o nome de exibição. Não exige "maiúscula + número + símbolo": tamanho vale
 * mais que complexidade (NIST SP 800-63B).
 */
@Component
public class PasswordPolicy {

    public static final int MIN = 12;
    public static final int MAX = 128;

    private final Set<String> common = new HashSet<>();

    public PasswordPolicy() {
        try (var in = getClass().getResourceAsStream("/security/common-passwords.txt")) {
            if (in != null) {
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                        .map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).forEach(s -> common.add(fold(s)));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Devolve todas as violações de uma vez, em pt-BR, para o 422 listar tudo. */
    public List<String> validate(String password, String email, String displayName) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            problems.add("Informe uma senha.");
            return problems;
        }
        if (password.length() < MIN) problems.add("Use pelo menos " + MIN + " caracteres; uma frase é mais fácil de lembrar.");
        if (password.length() > MAX) problems.add("Use no máximo " + MAX + " caracteres.");
        String folded = fold(password);
        if (common.contains(folded)) problems.add("Essa senha é muito comum; escolha outra.");
        if (email != null && !email.isBlank()) {
            String e = fold(email);
            String local = e.contains("@") ? e.substring(0, e.indexOf('@')) : e;
            if (folded.contains(e) || (local.length() >= 4 && folded.contains(local))) problems.add("A senha não pode conter seu e-mail.");
        }
        if (displayName != null && displayName.trim().length() >= 4 && folded.contains(fold(displayName.trim()))) {
            problems.add("A senha não pode conter seu nome.");
        }
        return problems;
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }
}
