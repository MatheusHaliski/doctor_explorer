package com.doctorexplorer.clinical.crypto;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Busca por nome sem guardar o nome em claro: para cada palavra (sem acento, minúscula) guarda o HMAC de cada prefixo
 * de 2 a 20 letras, com chave própria do cofre. A consulta calcula o HMAC das palavras digitadas e exige todas.
 * O banco nunca vê o nome nem o termo buscado; cofres diferentes geram tokens diferentes para o mesmo nome.
 */
@Component
public class BlindIndex {

    static final int MIN = 2;
    static final int MAX = 20;

    private final byte[] key;

    public BlindIndex(ClinicalProperties props) {
        this.key = props.subkey("patient-name-index");
    }

    public static List<String> words(String name) {
        if (name == null) return List.of();
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^\\p{L}\\p{N}]+")).filter(w -> w.length() >= MIN).toList();
    }

    /** Tokens gravados para o nome (todos os prefixos de cada palavra). */
    public Set<String> tokensFor(UUID vaultId, String name) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String w : words(name)) for (int n = MIN; n <= Math.min(MAX, w.length()); n++) prefixes.add(w.substring(0, n));
        Set<String> out = new LinkedHashSet<>();
        prefixes.forEach(p -> out.add(token(vaultId, p)));
        return out;
    }

    /** Tokens da consulta: uma palavra digitada vale como prefixo. */
    public List<String> queryTokens(UUID vaultId, String query) {
        return words(query).stream().distinct().map(w -> token(vaultId, w.length() > MAX ? w.substring(0, MAX) : w)).toList();
    }

    /** 128 bits do HMAC, em hexadecimal. */
    private String token(UUID vaultId, String prefix) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] vaultKey = mac.doFinal(vaultId.toString().getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(vaultKey, "HmacSHA256"));
            return HexFormat.of().formatHex(Arrays.copyOf(mac.doFinal(prefix.getBytes(StandardCharsets.UTF_8)), 16));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
