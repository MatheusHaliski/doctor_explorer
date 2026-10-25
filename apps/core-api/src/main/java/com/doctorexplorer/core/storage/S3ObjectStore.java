package com.doctorexplorer.core.storage;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
public class S3ObjectStore implements ObjectStore {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStore.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final boolean sse;

    public S3ObjectStore(StorageProperties p) {
        AwsCredentialsProvider creds = p.accessKey() == null || p.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey()));
        // S3 compatível (Garage) não aceita os checksums automáticos nem o envio em blocos dos SDKs recentes:
        // calcula só quando a operação exige e assina o corpo inteiro. Na AWS isso também é válido.
        S3Configuration cfg = S3Configuration.builder().pathStyleAccessEnabled(p.pathStyle()).chunkedEncodingEnabled(false).build();
        var builder = S3Client.builder().region(Region.of(p.region())).credentialsProvider(creds).serviceConfiguration(cfg)
                .requestChecksumCalculation(software.amazon.awssdk.core.checksums.RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(software.amazon.awssdk.core.checksums.ResponseChecksumValidation.WHEN_REQUIRED);
        var pre = S3Presigner.builder().region(Region.of(p.region())).credentialsProvider(creds).serviceConfiguration(cfg);
        if (p.endpoint() != null && !p.endpoint().isBlank()) builder.endpointOverride(URI.create(p.endpoint()));
        String publicEndpoint = p.publicEndpoint() == null || p.publicEndpoint().isBlank() ? p.endpoint() : p.publicEndpoint();
        if (publicEndpoint != null && !publicEndpoint.isBlank()) pre.endpointOverride(URI.create(publicEndpoint));
        this.s3 = builder.build();
        this.presigner = pre.build();
        this.sse = p.serverSideEncryption();
        if (p.createBuckets()) {
            for (String b : List.of(p.buckets().auditExports(), p.buckets().mediaQuarantine(), p.buckets().mediaPublished(), p.buckets().chatAttachments())) {
                try {
                    s3.headBucket(r -> r.bucket(b));
                } catch (RuntimeException missing) {
                    s3.createBucket(r -> r.bucket(b));
                    log.info("Bucket {} criado (doctor-explorer.storage.create-buckets=true)", b);
                }
            }
        }
    }

    @Override
    public void put(String bucket, String key, byte[] bytes, String contentType, Map<String, String> metadata) {
        s3.putObject(r -> {
            r.bucket(bucket).key(key).contentType(contentType).metadata(metadata == null ? Map.of() : metadata);
            if (sse) r.serverSideEncryption(software.amazon.awssdk.services.s3.model.ServerSideEncryption.AES256);
        }, RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<StoredObject> get(String bucket, String key) {
        try {
            ResponseBytes<GetObjectResponse> rb = s3.getObjectAsBytes(r -> r.bucket(bucket).key(key));
            GetObjectResponse h = rb.response();
            return Optional.of(new StoredObject(rb.asByteArray(), h.contentType(), h.contentLength(), h.metadata()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String bucket, String key) {
        s3.deleteObject(r -> r.bucket(bucket).key(key));
    }

    @Override
    public Optional<Long> size(String bucket, String key) {
        try {
            return Optional.of(s3.headObject(r -> r.bucket(bucket).key(key)).contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) return Optional.empty();
            throw e;
        }
    }

    @Override
    public void copy(String fromBucket, String fromKey, String toBucket, String toKey) {
        s3.copyObject(r -> r.sourceBucket(fromBucket).sourceKey(fromKey).destinationBucket(toBucket).destinationKey(toKey));
    }

    @Override
    public void allowBrowserUploads(String bucket, List<String> origins) {
        if (origins == null || origins.isEmpty()) return;
        s3.putBucketCors(r -> r.bucket(bucket).corsConfiguration(c -> c.corsRules(rule -> rule.allowedOrigins(origins)
                .allowedMethods("PUT", "GET").allowedHeaders("content-type").maxAgeSeconds(600))));
    }

    @Override
    public URI presignGet(String bucket, String key, Duration ttl, String downloadName) {
        return URI.create(presigner.presignGetObject(r -> r.signatureDuration(ttl).getObjectRequest(g -> {
            g.bucket(bucket).key(key);
            if (downloadName != null) g.responseContentDisposition("attachment; filename=\"" + downloadName.replace("\"", "") + "\"");
        })).url().toString());
    }

    @Override
    public URI presignPut(String bucket, String key, String contentType, long contentLength, Duration ttl) {
        return URI.create(presigner.presignPutObject(r -> r.signatureDuration(ttl)
                .putObjectRequest(p -> p.bucket(bucket).key(key).contentType(contentType).contentLength(contentLength))).url().toString());
    }
}
