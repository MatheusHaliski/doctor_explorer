package com.doctorexplorer.core.credentialing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RF02 · Reverifica todos os selos VERIFIED uma vez por dia (02:30). */
@Component
public class ReverificationJob {

    private static final Logger log = LoggerFactory.getLogger(ReverificationJob.class);
    private final CredentialingService credentialing;

    public ReverificationJob(CredentialingService credentialing) {
        this.credentialing = credentialing;
    }

    @Scheduled(cron = "${doctor-explorer.jobs.reverification-cron:0 30 2 * * *}")
    public void run() {
        int revoked = credentialing.reverifyAll();
        log.info("Reverificação de selos concluída: {} selo(s) revogado(s)", revoked);
    }
}
