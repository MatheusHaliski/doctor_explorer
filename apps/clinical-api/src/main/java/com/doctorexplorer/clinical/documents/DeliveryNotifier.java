package com.doctorexplorer.clinical.documents;

/**
 * Envio do link ao paciente (e-mail ou SMS). A mensagem nunca leva conteúdo clínico (LGPD): só quem enviou e o link
 * de uso único. Em produção, SES e um provedor de SMS; no MVP, {@link LoggingDeliveryNotifier}.
 */
public interface DeliveryNotifier {

    void send(String channel, String destination, String doctorName, String link);
}
