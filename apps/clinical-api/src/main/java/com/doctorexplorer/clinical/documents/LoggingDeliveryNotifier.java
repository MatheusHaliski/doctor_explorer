package com.doctorexplorer.clinical.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** MVP: registra que um envio aconteceu, com o destino mascarado e sem o link (o token é credencial de acesso). */
@Component
public class LoggingDeliveryNotifier implements DeliveryNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingDeliveryNotifier.class);

    @Override
    public void send(String channel, String destination, String doctorName, String link) {
        log.info("[entrega-dev] {} para {}: \"{} enviou um documento para você. O link vale 7 dias e abre uma vez.\"", channel,
                DocumentService.mask(channel, destination), doctorName);
    }
}
