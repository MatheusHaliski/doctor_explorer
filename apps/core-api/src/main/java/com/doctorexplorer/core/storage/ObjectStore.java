package com.doctorexplorer.core.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Armazenamento de objetos (RF05, RF13). Implementação: {@link S3ObjectStore}. */
public interface ObjectStore {

    record StoredObject(byte[] bytes, String contentType, long size, Map<String, String> metadata) {}

    void put(String bucket, String key, byte[] bytes, String contentType, Map<String, String> metadata);

    Optional<StoredObject> get(String bucket, String key);

    void delete(String bucket, String key);

    /** URL de leitura temporária; {@code downloadName} vira Content-Disposition de anexo quando informado. */
    URI presignGet(String bucket, String key, Duration ttl, String downloadName);

    /** URL de envio temporária com tipo e tamanho fixos na assinatura (RF05, critério 1). */
    URI presignPut(String bucket, String key, String contentType, long contentLength, Duration ttl);
}
