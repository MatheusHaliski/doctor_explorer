package com.doctorexplorer.core.privacy.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RF10 · Job diário (03:15, configurável) que roda a saga de eliminação para as carências vencidas. */
@Component
public class ErasureJob {

    private static final Logger log = LoggerFactory.getLogger(ErasureJob.class);
    private final ErasureSaga saga;

    public ErasureJob(ErasureSaga saga) {
        this.saga = saga;
    }

    @Scheduled(cron = "${doctor-explorer.jobs.erasure-cron:0 15 3 * * *}")
    public void run() {
        var receipts = saga.runDue(Instant.now());
        log.info("Saga de eliminação: {} conta(s) eliminada(s)", receipts.size());
    }
}
