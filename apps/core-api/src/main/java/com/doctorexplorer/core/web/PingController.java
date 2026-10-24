package com.doctorexplorer.core.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de verificação do esqueleto: cabeçalhos de segurança, CSRF e formato de erro. Some no passo 2. */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    public record Echo(
            @NotBlank(message = "Informe uma mensagem.") @Size(max = 140, message = "Use no máximo 140 caracteres.") String message) {}

    @GetMapping
    Map<String, Object> ping() {
        return Map.of("service", "core-api", "time", Instant.now().toString());
    }

    @PostMapping
    Map<String, Object> echo(@Valid @RequestBody Echo echo) {
        return Map.of("echo", echo.message());
    }
}
