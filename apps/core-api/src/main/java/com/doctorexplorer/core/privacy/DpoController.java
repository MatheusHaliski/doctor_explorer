package com.doctorexplorer.core.privacy;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF10 · /api/v1/privacy/dpo: só o Encarregado com 2FA (SecurityConfig). A fila de pedidos do titular
 * (DpoTicket) entra na fase 5; por ora devolve a estrutura vazia para a matriz de acesso ser testável.
 */
@RestController
@RequestMapping("/api/v1/privacy/dpo")
public class DpoController {

    @GetMapping("/tickets")
    Map<String, Object> tickets() {
        return Map.of("tickets", List.of(), "note", "fila do Encarregado na fase 5 (RF10)");
    }
}
