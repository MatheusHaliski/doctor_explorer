package com.doctorexplorer.core.identity.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mailer de dev/it: registra no log e guarda os últimos envios em memória para os testes lerem o código.
 * O mailer de produção (SES/SMTP) entra como bean {@code @Primary} e este deixa de ser usado.
 */
@Component
public class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    public record Sent(String to, String subject, String body) {}

    private final List<Sent> outbox = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(String to, String subject, String body) {
        outbox.add(new Sent(to, subject, body));
        log.info("[mailer-dev] para={} assunto={}\n{}", to, subject, body);
    }

    public List<Sent> outbox() {
        return List.copyOf(outbox);
    }

    public void clear() {
        outbox.clear();
    }
}
