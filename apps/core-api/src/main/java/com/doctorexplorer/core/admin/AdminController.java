package com.doctorexplorer.core.admin;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF13 · /api/v1/admin: protegido por papel MODERATOR/ADMIN + 2FA (SecurityConfig). A consulta de
 * auditoria real (Cassandra, hash encadeado) entra na fase 11; por ora devolve a estrutura vazia para a
 * matriz de acesso ser testável de ponta a ponta.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/audit")
    Map<String, Object> audit() {
        return Map.of("events", List.of(), "chainIntact", true, "note", "auditoria completa na fase 11 (RF13)");
    }
}
