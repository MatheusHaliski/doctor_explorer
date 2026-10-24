package com.doctorexplorer.core.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doctorexplorer.core.config.AppSecretValidator;
import com.doctorexplorer.core.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Sem banco: só a fatia web + SecurityFilterChain. Garante os cabeçalhos, o CSRF e o formato Problem Details. */
@WebMvcTest(controllers = PingController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, AppSecretValidator.class})
@ActiveProfiles("it")
class SecurityHeadersTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicEndpointCarriesHardenedHeaders() throws Exception {
        mvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.service").value("core-api"));
    }

    @Test
    void writeWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/ping").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"oi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationReturnsProblemDetails422WithAllErrorsInPtBr() throws Exception {
        mvc.perform(post("/api/v1/ping").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
                .andExpect(status().is(422))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.detail").value("Há 1 campo para corrigir."))
                .andExpect(jsonPath("$.errors[0].field").value("message"))
                .andExpect(jsonPath("$.errors[0].message").value("Informe uma mensagem."));
    }

    @Test
    void protectedEndpointRequiresSession() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
