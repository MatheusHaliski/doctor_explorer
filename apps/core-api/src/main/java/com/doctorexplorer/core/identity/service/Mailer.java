package com.doctorexplorer.core.identity.service;

/** Envio de e-mail transacional. Em produção: SES/SMTP (ADR futuro). Nunca leva dado de saúde. */
public interface Mailer {

    void send(String to, String subject, String body);
}
