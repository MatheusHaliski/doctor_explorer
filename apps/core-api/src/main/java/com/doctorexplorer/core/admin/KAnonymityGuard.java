package com.doctorexplorer.core.admin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RF13 · k-anonimato: toda célula de métrica vem com o número de pessoas distintas que a formam. Abaixo de
 * {@value #MIN_CELL} pessoas o valor é suprimido (nunca zero, nunca o número de pessoas): a interface mostra
 * "menos de 10 pessoas". Não há drill-down para indivíduo.
 */
public final class KAnonymityGuard {

    public static final int MIN_CELL = 10;

    private KAnonymityGuard() {}

    /** Célula pronta para JSON: {value, suppressed}. */
    public static Map<String, Object> cell(Number value, long distinctPeople) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean suppressed = distinctPeople < MIN_CELL;
        m.put("value", suppressed ? null : value);
        m.put("suppressed", suppressed);
        return m;
    }
}
