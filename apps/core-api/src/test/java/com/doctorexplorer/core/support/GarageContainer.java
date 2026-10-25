package com.doctorexplorer.core.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * Garage (S3 compatível, o mesmo do compose.yaml) para os testes de integração: um nó, chave fixa de teste e os
 * buckets da aplicação, criados pela Admin API v2 como faz infra/garage/init.sh.
 */
public class GarageContainer extends GenericContainer<GarageContainer> {

    public static final String ACCESS_KEY = "GKdedevdedevdedevdedevdede";
    public static final String SECRET_KEY = "de-dev-secret-de-dev-secret-de-dev-secret-de-dev-secret-0000000";
    static final String ADMIN_TOKEN = "dev-admin-token";
    static final List<String> BUCKETS = List.of("de-audit-exports", "de-media-quarantine", "de-media-published", "de-chat-attachments");

    private static final String CONFIG = """
            metadata_dir = "/var/lib/garage/meta"
            data_dir = "/var/lib/garage/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "0.0.0.0:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "0000000000000000000000000000000000000000000000000000000000000000"

            [s3_api]
            s3_region = "garage"
            api_bind_addr = "0.0.0.0:3900"
            root_domain = ".s3.garage.localhost"

            [admin]
            api_bind_addr = "0.0.0.0:3903"
            admin_token = "dev-admin-token"
            """;

    public GarageContainer() {
        super(DockerImageName.parse("dxflrs/garage:v2.4.1"));
        withCopyToContainer(Transferable.of(CONFIG), "/etc/garage.toml");
        withExposedPorts(3900, 3903);
        waitingFor(Wait.forHttp("/health").forPort(3903).forStatusCodeMatching(c -> c == 200 || c == 503));
    }

    public String s3Url() {
        return "http://" + getHost() + ":" + getMappedPort(3900);
    }

    @Override
    protected void containerIsStarted(com.github.dockerjava.api.command.InspectContainerResponse info) {
        try {
            init();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao inicializar o Garage de teste", e);
        }
    }

    private void init() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String admin = "http://" + getHost() + ":" + getMappedPort(3903) + "/v2/";
        String status = call(http, admin + "GetClusterStatus", null);
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f]{16,})\"").matcher(status);
        if (!m.find()) throw new IllegalStateException("nó do Garage não encontrado: " + status);
        String node = m.group(1);
        call(http, admin + "UpdateClusterLayout", "{\"roles\":[{\"id\":\"" + node + "\",\"zone\":\"it\",\"capacity\":1073741824,\"tags\":[\"it\"]}]}");
        String layout = call(http, admin + "GetClusterLayout", null);
        Matcher v = Pattern.compile("\"version\"\\s*:\\s*(\\d+)").matcher(layout);
        int version = v.find() ? Integer.parseInt(v.group(1)) : 0;
        call(http, admin + "ApplyClusterLayout", "{\"version\":" + (version + 1) + "}");
        call(http, admin + "ImportKey", "{\"name\":\"de-it\",\"accessKeyId\":\"" + ACCESS_KEY + "\",\"secretAccessKey\":\"" + SECRET_KEY + "\"}");
        for (String b : BUCKETS) {
            String created = call(http, admin + "CreateBucket", "{\"globalAlias\":\"" + b + "\"}");
            Matcher id = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f]+)\"").matcher(created);
            if (!id.find()) throw new IllegalStateException("bucket sem id: " + created);
            call(http, admin + "AllowBucketKey", "{\"bucketId\":\"" + id.group(1) + "\",\"accessKeyId\":\"" + ACCESS_KEY
                    + "\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":true}}");
        }
    }

    private static String call(HttpClient http, String url, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + ADMIN_TOKEN);
        b = body == null ? b.GET() : b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 300) throw new IllegalStateException(url + " → " + r.statusCode() + " " + r.body());
        return r.body();
    }
}
