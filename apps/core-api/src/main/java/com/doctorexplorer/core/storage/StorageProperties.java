package com.doctorexplorer.core.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * doctor-explorer.storage.*: S3 (produção, AWS sa-east-1) ou S3 compatível em dev/it (Garage, MinIO).
 * {@code endpoint} vazio = AWS; {@code publicEndpoint} é o endereço que o navegador usa nas URLs
 * pré-assinadas (em dev o contêiner pode ter outro nome dentro da rede do Compose). {@code serverSideEncryption}
 * liga SSE-S3 (AES-256) em produção; Garage e MinIO sem KMS recusam o cabeçalho, então fica desligado em dev/it.
 */
@ConfigurationProperties("doctor-explorer.storage")
public record StorageProperties(String endpoint, String publicEndpoint, String region, String accessKey, String secretKey,
        boolean pathStyle, boolean createBuckets, boolean serverSideEncryption, Buckets buckets) {

    public record Buckets(String auditExports, String mediaQuarantine, String mediaPublished) {
        public Buckets {
            if (auditExports == null) auditExports = "de-audit-exports";
            if (mediaQuarantine == null) mediaQuarantine = "de-media-quarantine";
            if (mediaPublished == null) mediaPublished = "de-media-published";
        }
    }

    public StorageProperties {
        if (region == null || region.isBlank()) region = "sa-east-1";
        if (buckets == null) buckets = new Buckets(null, null, null);
    }
}
