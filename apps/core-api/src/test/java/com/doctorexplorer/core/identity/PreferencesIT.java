package com.doctorexplorer.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestInfra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** RF01 · Aparência: tema, fonte e arte de fundo com intensidade; validação agrupada; entra em "seus dados". */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class PreferencesIT {

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void defaultsUpdateAndValidation() throws Exception {
        ApiClient c = new ApiClient(mvc);
        assertThat(c.get("/api/v1/me/preferences").getStatus()).isEqualTo(401);
        c.register("prefs+" + System.nanoTime() + "@example.org", "frase longa e segura 2026");
        c.activateEmail(mailer);

        String defaults = c.get("/api/v1/me/preferences").getContentAsString();
        assertThat(defaults).contains("\"theme\":\"system\"").contains("\"background\":\"none\"").contains("\"backgroundVeil\":45")
                .contains("\"backgrounds\":[\"none\",\"aurora-clara\",\"neon-escuro\",\"azul-noturno\",\"ambar\",\"neon-colorido\"]");

        // só o que vem preenchido muda
        MockHttpServletResponse ok = c.put("/api/v1/me/preferences", "{\"background\":\"Neon-Escuro\",\"backgroundVeil\":20,\"theme\":\"dark\"}");
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getContentAsString()).contains("\"background\":\"neon-escuro\"").contains("\"backgroundVeil\":20").contains("\"theme\":\"dark\"").contains("\"fontScale\":100");

        // todos os erros juntos (422)
        MockHttpServletResponse bad = c.put("/api/v1/me/preferences", "{\"background\":\"praia\",\"backgroundVeil\":5,\"theme\":\"sepia\",\"fontScale\":300}");
        assertThat(bad.getStatus()).isEqualTo(422);
        assertThat(bad.getContentAsString()).contains("Há 4 campos para corrigir.").contains("\"field\":\"background\"")
                .contains("\"field\":\"backgroundVeil\"").contains("\"field\":\"theme\"").contains("\"field\":\"fontScale\"");
        assertThat(c.get("/api/v1/me/preferences").getContentAsString()).contains("\"background\":\"neon-escuro\""); // nada mudou

        // LGPD: a preferência aparece em "seus dados"
        assertThat(c.get("/api/v1/privacy/data").getContentAsString()).contains("\"arteDeFundo\":\"neon-escuro\"").contains("\"intensidadeDoVeu\":20");
    }
}
