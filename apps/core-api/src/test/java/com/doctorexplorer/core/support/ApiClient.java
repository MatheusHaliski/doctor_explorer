package com.doctorexplorer.core.support;

import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cliente de teste com sessão de verdade: guarda os cookies (sessão opaca + XSRF-TOKEN) entre chamadas,
 * pega o CSRF em /auth/csrf e manda X-XSRF-TOKEN em toda escrita. Nada de mock de autenticação.
 */
public class ApiClient {

    private final MockMvc mvc;
    private final Map<String, Cookie> jar = new HashMap<>();

    public ApiClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public MockHttpServletResponse get(String path) throws Exception {
        return exchange(MockMvcRequestBuilders.get(path));
    }

    public MockHttpServletResponse post(String path, String json) throws Exception {
        if (!jar.containsKey("XSRF-TOKEN")) get("/api/v1/auth/csrf");
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(path).header("X-XSRF-TOKEN", jar.get("XSRF-TOKEN").getValue());
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return exchange(b);
    }

    /** Cadastro completo com valores válidos; devolve a resposta para o teste inspecionar. */
    public MockHttpServletResponse register(String email, String password) throws Exception {
        return post("/api/v1/auth/register", """
                {"email":"%s","displayName":"Dra. Ana Ribeiro","birthDate":"1990-05-10","password":"%s",
                 "acceptTerms":true,"acceptPrivacy":true,"termsVersion":"2026-10-24","privacyVersion":"2026-10-24"}
                """.formatted(email, password));
    }

    public MockHttpServletResponse login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    public boolean hasSessionCookie() {
        return jar.keySet().stream().anyMatch(n -> n.endsWith("de_session"));
    }

    public void forgetSession() {
        jar.keySet().removeIf(n -> n.endsWith("de_session"));
    }

    private MockHttpServletResponse exchange(MockHttpServletRequestBuilder b) throws Exception {
        b.header("User-Agent", "ApiClient/test").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; });
        if (!jar.isEmpty()) b.cookie(jar.values().toArray(Cookie[]::new));
        MvcResult result = mvc.perform(b).andReturn();
        MockHttpServletResponse res = result.getResponse();
        // Spring Session escreve Set-Cookie como cabeçalho (não via addCookie); lemos os dois caminhos.
        for (Cookie c : res.getCookies()) remember(c.getName(), c.getValue(), c.getMaxAge());
        for (String header : res.getHeaders("Set-Cookie")) {
            String[] parts = header.split(";");
            String[] nv = parts[0].split("=", 2);
            int maxAge = -1;
            for (String p : parts) {
                String t = p.trim();
                if (t.regionMatches(true, 0, "Max-Age=", 0, 8)) maxAge = Integer.parseInt(t.substring(8).trim());
            }
            remember(nv[0].trim(), nv.length > 1 ? nv[1].trim() : "", maxAge);
        }
        return res;
    }

    private void remember(String name, String value, int maxAge) {
        if (maxAge == 0 || value.isEmpty()) jar.remove(name); else jar.put(name, new Cookie(name, value));
    }
}
