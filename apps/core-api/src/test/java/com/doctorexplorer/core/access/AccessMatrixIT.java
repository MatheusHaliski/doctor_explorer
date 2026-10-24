package com.doctorexplorer.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.credentialing.domain.UserRole;
import com.doctorexplorer.core.credentialing.repo.UserRoles;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.identity.service.Totp;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestInfra;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RF12 · A matriz de acesso em docs/rf/RF12-rbac-hierarquico.md é a mesma que a implementação aplica.
 * Este teste lê a tabela do doc e exercita as linhas cujas rotas já existem; linhas de rotas futuras são
 * ignoradas e listadas no log, para o doc continuar sendo a fonte da verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
public class AccessMatrixIT {

    static final Path DOC = Path.of("../../docs/rf/RF12-rbac-hierarquico.md");
    static final String PASSWORD = "frase longa e segura 2026";

    /** Rotas já implementadas: rótulo da tabela → requisição concreta (método + caminho + corpo). */
    static final Map<String, String[]> ROUTES = Map.of(
            "`POST /credentials`", new String[] {"POST", "/api/v1/credentials", "{\"crmNumber\":\"CRM\",\"crmUf\":\"PR\",\"specialties\":[]}"},
            "`GET /groups/**`", new String[] {"GET", "/api/v1/groups", null},
            "`POST /posts`, comentar", new String[] {"POST", "/api/v1/posts", "{\"specialtyCode\":\"UTI\",\"kind\":\"EDUCATION\",\"audience\":\"NETWORK\",\"title\":\"Caso da semana de teste\",\"question\":\"Pergunta guiada\"}"},
            "`GET /admin/audit`", new String[] {"GET", "/api/v1/admin/audit", null},
            "`/admin/moderation/**` (fila, selo, grupo)", new String[] {"GET", "/api/v1/admin/moderation/queue", null},
            "`GET /institutions/{id}/members` (gestão)", new String[] {"GET", "/api/v1/institutions/0199a000-0000-7000-8000-0000000000ff/members", null},
            "`GET /privacy/dpo/tickets`", new String[] {"GET", "/api/v1/privacy/dpo/tickets", null},
            "`/me/**`", new String[] {"GET", "/api/v1/me", null});

    /** Colunas da tabela do doc, na ordem. */
    static final List<String> ACTORS = List.of("visitante", "pendente", "ativo", "verificado", "gestor", "moderador", "dpo");

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired UserAccounts accounts;
    @Autowired UserRoles roles;

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void implementedRowsOfTheMatrixMatchTheDoc() throws Exception {
        List<String[]> rows = parseMatrix(Files.readString(DOC));
        assertThat(rows).isNotEmpty();
        List<String> checked = new ArrayList<>();
        for (String[] row : rows) {
            String[] route = ROUTES.get(row[0].trim());
            if (route == null) continue;
            for (int i = 0; i < ACTORS.size(); i++) {
                String actor = ACTORS.get(i);
                String cell = row[i + 1].trim();
                if (actor.equals("gestor")) continue; // papéis institucionais entram com o RF11
                if (cell.startsWith("—")) continue; // não se aplica a esse ator
                boolean allowed = cell.startsWith("✅");
                ApiClient client = clientFor(actor);
                String body = route[2] == null ? null : route[2].replace("CRM", uniqueCrm());
                int status = route[0].equals("GET") ? client.get(route[1]).getStatus() : client.post(route[1], body).getStatus();
                if (allowed) {
                    assertThat(status).as("%s × %s deveria permitir (doc: %s)", row[0], actor, cell).isIn(200, 201, 202, 204);
                } else {
                    assertThat(status).as("%s × %s deveria negar (doc: %s)", row[0], actor, cell).isIn(401, 403, 404);
                }
                checked.add(row[0] + " × " + actor + " → " + status);
            }
        }
        assertThat(checked).as("linhas exercitadas").hasSizeGreaterThanOrEqualTo(ROUTES.size() * 5);
    }

    private ApiClient clientFor(String actor) throws Exception {
        ApiClient c = new ApiClient(mvc);
        if (actor.equals("visitante")) return c;
        String email = actor + "+" + System.nanoTime() + "@example.org";
        c.register(email, PASSWORD);
        if (actor.equals("pendente")) return c;
        activate(c);
        if (actor.equals("verificado")) c.post("/api/v1/credentials", "{\"crmNumber\":\"" + uniqueCrm() + "\",\"crmUf\":\"PR\",\"specialties\":[]}");
        if (actor.equals("moderador") || actor.equals("dpo")) {
            var account = accounts.findByEmailNormalized(email).orElseThrow();
            roles.save(new UserRole(account.getId(), actor.equals("dpo") ? UserRole.Role.DPO : UserRole.Role.MODERATOR, null));
            enableMfaAndRelogin(c, email);
        }
        return c;
    }

    private void activate(ApiClient c) throws Exception {
        var sent = mailer.outbox();
        Matcher m = Pattern.compile("Seu código é (\\d{6})").matcher(sent.get(sent.size() - 1).body());
        assertThat(m.find()).isTrue();
        assertThat(c.post("/api/v1/me/verify-email", "{\"code\":\"" + m.group(1) + "\"}").getStatus()).isEqualTo(200);
    }

    private void enableMfaAndRelogin(ApiClient c, String email) throws Exception {
        c.login(email, PASSWORD);
        c.post("/api/v1/me/sudo", "{\"password\":\"" + PASSWORD + "\"}");
        String setup = c.post("/api/v1/me/mfa/totp/setup", null).getContentAsString();
        Matcher m = Pattern.compile("\"secret\":\"([A-Z2-7]+)\"").matcher(setup);
        assertThat(m.find()).isTrue();
        byte[] secret = base32Decode(m.group(1));
        assertThat(c.post("/api/v1/me/mfa/totp/confirm", "{\"code\":\"" + Totp.codeAt(secret, Instant.now().getEpochSecond()) + "\"}").getStatus()).isEqualTo(200);
        c.post("/api/v1/auth/logout", null);
        c.login(email, PASSWORD);
        assertThat(c.post("/api/v1/auth/login/2fa", "{\"code\":\"" + Totp.codeAt(secret, Instant.now().getEpochSecond()) + "\"}").getStatus()).isEqualTo(200);
    }

    /** Prefixo 12 = Regular "Ana Ribeiro" na fonte simulada; sufixo único para não colidir entre atores. */
    private static String uniqueCrm() {
        return "12" + String.format("%04d", System.nanoTime() % 10_000);
    }

    /** Lê as linhas da seção "## Matriz de acesso": `| rota | visitante | … |`. */
    static List<String[]> parseMatrix(String md) {
        String section = md.substring(md.indexOf("## Matriz de acesso"));
        section = section.substring(0, section.indexOf("\n## ", 10));
        List<String[]> rows = new ArrayList<>();
        for (String line : section.split("\n")) {
            if (!line.startsWith("|") || line.startsWith("| Rota") || line.startsWith("|---")) continue;
            String[] cells = line.substring(1, line.lastIndexOf('|')).split("\\|");
            if (cells.length == ACTORS.size() + 1) rows.add(cells);
        }
        return rows;
    }

    public static byte[] base32Decode(String s) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char ch : s.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(ch);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
