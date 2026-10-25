package com.doctorexplorer.core.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CRM único por JVM de teste. Os *IT dividem o mesmo banco e o sistema recusa CRM já verificado em outra conta;
 * sortear entre poucos valores colidia (paradoxo do aniversário). Prefixo de 2 dígitos (regra da fonte simulada)
 * + contador de 5 dígitos = 7 dígitos, o máximo aceito.
 */
public final class TestCrm {

    private static final AtomicInteger NEXT = new AtomicInteger(ThreadLocalRandom.current().nextInt(50_000));

    private TestCrm() {}

    public static String next(String prefix) {
        return prefix + String.format("%05d", NEXT.getAndIncrement() % 100_000);
    }
}
