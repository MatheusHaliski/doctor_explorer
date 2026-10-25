package com.doctorexplorer.clinical.common;

import java.security.SecureRandom;
import java.util.UUID;

/** UUID v7 (RFC 9562): 48 bits de timestamp em ms + 74 bits aleatórios. Ordenável por tempo, bom para chave primária. */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    public static UUID generate() {
        long ms = System.currentTimeMillis();
        byte[] rnd = new byte[10];
        RANDOM.nextBytes(rnd);
        long msb = (ms << 16) | 0x7000L | ((rnd[0] & 0x0FL) << 8) | (rnd[1] & 0xFFL);
        long lsb = 0x8000000000000000L; // variante RFC 4122
        for (int i = 2; i < 10; i++) {
            lsb |= (rnd[i] & 0xFFL) << (8 * (9 - i));
        }
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
