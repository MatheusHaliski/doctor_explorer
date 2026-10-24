package com.doctorexplorer.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Cookie da sessão opaca: {@code __Host-de_session}, HttpOnly, Secure, SameSite=Lax, Path=/, sem Max-Age
 * (cookie de sessão do navegador). Explícito aqui para não depender do mapeamento de propriedades do Boot.
 * Em dev/it sem TLS o nome perde o prefixo __Host- e Secure fica desligado (perfis).
 */
@Configuration
public class SessionConfig {

    @Bean
    CookieSerializer cookieSerializer(AppSecretProperties props) {
        DefaultCookieSerializer s = new DefaultCookieSerializer();
        s.setCookieName(props.session().cookieName());
        s.setUseHttpOnlyCookie(true);
        s.setUseSecureCookie(props.session().cookieSecure());
        s.setSameSite("Lax");
        s.setCookiePath("/");
        s.setCookieMaxAge(-1);
        s.setUseBase64Encoding(true);
        return s;
    }
}
