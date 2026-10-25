package com.doctorexplorer.core.calls;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * doctor-explorer.rtc.*: onde o navegador encontra o SFU (apps/rtc) e o TURN. {@code turnSecret} é o segredo
 * compartilhado do coturn (REST API: usuário com validade e senha HMAC), sempre por variável de ambiente; vazio =
 * sem TURN (rede local em dev).
 */
@ConfigurationProperties("doctor-explorer.rtc")
public record CallProperties(@DefaultValue("ws://localhost:4443/ws") String publicUrl, @DefaultValue("8") int maxParticipants,
        @DefaultValue("45") int ringSeconds, List<String> turnUrls, String turnSecret, @DefaultValue("600") int turnTtlSeconds) {

    public CallProperties {
        if (turnUrls == null) turnUrls = List.of();
        if (turnSecret == null) turnSecret = "";
    }
}
