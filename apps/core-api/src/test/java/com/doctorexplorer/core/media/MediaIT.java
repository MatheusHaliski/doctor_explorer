package com.doctorexplorer.core.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.storage.ObjectStore;
import com.doctorexplorer.core.storage.StorageProperties;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestCrm;
import com.doctorexplorer.core.support.TestInfra;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RF05 · ponta a ponta com S3 real (Garage) e ferramentas reais: URL pré-assinada, quarentena, anonimização em segundo
 * plano, prévia privada, tarja manual, aprovação, anexo à publicação e limpeza do original.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class MediaIT {

    static final String PASSWORD = "frase longa e segura 2026";
    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    static final Pattern UPLOAD_URL = Pattern.compile("\"uploadUrl\":\"([^\"]+)\"");
    static final Pattern VERSION = Pattern.compile("\"previewVersion\":(\\d+)");
    static final Pattern STATE = Pattern.compile("\"state\":\"([A-Z_]+)\"");

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired MediaAssets assets;
    @Autowired MediaService media;
    @Autowired ObjectStore objects;
    @Autowired StorageProperties storage;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ApiClient verifiedDoctor() throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register("midia+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        String crm = TestCrm.next("12");
        c.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm + "\",\"crmUf\":\"PR\",\"specialties\":[{\"specialtyCode\":\"UTI\",\"rqe\":\"11111\"}]}");
        c.post("/api/v1/groups/UTI/join", null);
        return c;
    }

    private record Started(UUID id, String uploadUrl) {}

    private Started requestUpload(ApiClient c, String type, int size, boolean dermatology) throws Exception {
        MockHttpServletResponse res = c.post("/api/v1/media/uploads",
                "{\"contentType\":\"" + type + "\",\"size\":" + size + ",\"dermatology\":" + dermatology + "}");
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        String body = res.getContentAsString();
        return new Started(UUID.fromString(group(ID, body)), group(UPLOAD_URL, body).replace("\\u0026", "&"));
    }

    private int put(String url, String type, byte[] bytes) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", type).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private byte[] fetch(String url) throws Exception {
        HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url.replace("\\u0026", "&"))).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(res.statusCode()).isEqualTo(200);
        return res.body();
    }

    /** Envia, conclui e espera o worker sair de QUARENTENA/PROCESSANDO. */
    private String uploadAndWait(ApiClient c, String type, byte[] bytes, boolean dermatology) throws Exception {
        Started s = requestUpload(c, type, bytes.length, dermatology);
        assertThat(put(s.uploadUrl(), type, bytes)).isEqualTo(200);
        assertThat(c.post("/api/v1/media/" + s.id() + "/complete", null).getStatus()).isEqualTo(202);
        Instant limit = Instant.now().plusSeconds(60);
        while (Instant.now().isBefore(limit)) {
            String body = c.get("/api/v1/media/" + s.id()).getContentAsString();
            String state = group(STATE, body);
            if (!state.equals("QUARENTENA") && !state.equals("PROCESSANDO")) return body;
            Thread.sleep(200);
        }
        throw new AssertionError("anonimização não terminou");
    }

    @Test
    void photoGoesFromQuarantineToReviewToPublishedPostWithoutMetadata() throws Exception {
        ApiClient c = verifiedDoctor();
        String review = uploadAndWait(c, "image/jpeg", MediaSamples.jpegWithExif(6), false);
        assertThat(review).contains("\"state\":\"AGUARDANDO_REVISAO\"").contains("\"gpsRemoved\":true").contains("\"exifOrientationApplied\":6")
                .contains("\"previewUrl\"").doesNotContain("\"url\"");
        UUID id = UUID.fromString(group(ID, review));
        MediaAsset asset = assets.findById(id).orElseThrow();

        // a prévia é privada, curta e já sem EXIF/GPS
        byte[] preview = fetch(group(Pattern.compile("\"previewUrl\":\"([^\"]+)\""), review));
        var dirs = StreamSupport.stream(ImageMetadataReader.readMetadata(new ByteArrayInputStream(preview)).getDirectories().spliterator(), false)
                .map(Directory::getName).toList();
        assertThat(dirs).noneMatch(d -> d.startsWith("Exif") || d.startsWith("GPS"));

        // tarja manual exige a versão atual da prévia e aumenta a versão
        assertThat(c.post("/api/v1/media/" + id + "/redactions", "{\"previewVersion\":99,\"regions\":[{\"x\":0.1,\"y\":0.1,\"w\":0.2,\"h\":0.2}]}").getStatus())
                .isEqualTo(409);
        MockHttpServletResponse redacted = c.post("/api/v1/media/" + id + "/redactions",
                "{\"previewVersion\":1,\"regions\":[{\"x\":0.1,\"y\":0.1,\"w\":0.2,\"h\":0.2}]}");
        assertThat(redacted.getStatus()).isEqualTo(200);
        assertThat(redacted.getContentAsString()).contains("\"previewVersion\":2").contains("\"manualRedactions\":1");

        // aprovar pede a confirmação explícita e a versão vista
        assertThat(c.post("/api/v1/media/" + id + "/approve", "{\"previewVersion\":2}").getStatus()).isEqualTo(422);
        assertThat(c.post("/api/v1/media/" + id + "/approve", "{\"previewVersion\":1,\"reviewed\":true}").getStatus()).isEqualTo(409);
        MockHttpServletResponse approved = c.post("/api/v1/media/" + id + "/approve", "{\"previewVersion\":2,\"reviewed\":true}");
        assertThat(approved.getContentAsString()).contains("\"state\":\"APROVADO\"").contains("\"url\"");

        // original e prévia saem da quarentena; só a versão anonimizada fica no bucket publicado
        assertThat(objects.size(storage.buckets().mediaQuarantine(), asset.getQuarantineKey())).isEmpty();
        assertThat(objects.size(storage.buckets().mediaQuarantine(), asset.getPreviewKey())).isEmpty();
        assertThat(assets.findById(id).orElseThrow().getOriginalDeletedAt()).isNotNull();

        MockHttpServletResponse post = c.post("/api/v1/posts", """
                {"specialtyCode":"UTI","kind":"CASE","title":"Lesão sacral em paciente sedado há 10 dias",
                 "complaint":"Lesão em evolução, foto anexada.","question":"Qual cobertura vocês usariam?","mediaIds":["%s"]}
                """.formatted(id));
        assertThat(post.getStatus()).as(post.getContentAsString()).isEqualTo(201);
        assertThat(post.getContentAsString()).contains("\"mediaCount\":1");
        String postId = group(ID, post.getContentAsString());
        String view = c.get("/api/v1/posts/" + postId).getContentAsString();
        assertThat(view).contains("\"media\":[{\"id\":\"" + id + "\"");
        byte[] published = fetch(group(Pattern.compile("\"media\":\\[\\{[^}]*\"url\":\"([^\"]+)\""), view));
        assertThat(published).isNotEmpty();

        // a mesma mídia não entra em outra publicação, e a publicada não é descartada por fora
        MockHttpServletResponse again = c.post("/api/v1/posts", """
                {"specialtyCode":"UTI","kind":"CASE","title":"Outra publicação com a mesma imagem","question":"Pode?","mediaIds":["%s"]}
                """.formatted(id));
        assertThat(again.getStatus()).isEqualTo(422);
        assertThat(c.delete("/api/v1/media/" + id).getStatus()).isEqualTo(409);
    }

    @Test
    void contentThatDoesNotMatchTheDeclaredTypeIsRejectedAndDeleted() throws Exception {
        ApiClient c = verifiedDoctor();
        String body = uploadAndWait(c, "image/jpeg", MediaSamples.png(40, 40), false);
        assertThat(body).contains("\"state\":\"REJEITADO\"").contains("image/png");
        MediaAsset a = assets.findById(UUID.fromString(group(ID, body))).orElseThrow();
        assertThat(objects.size(storage.buckets().mediaQuarantine(), a.getQuarantineKey())).isEmpty();
    }

    @Test
    void dicomIsDeidentifiedAndDermatologyKeepsFacesForManualReview() throws Exception {
        ApiClient c = verifiedDoctor();
        String dicom = uploadAndWait(c, "application/dicom", MediaSamples.dicom(), false);
        assertThat(dicom).contains("\"state\":\"AGUARDANDO_REVISAO\"").contains("\"kind\":\"DICOM\"").contains("PatientName").doesNotContain("SILVA");

        String derm = uploadAndWait(c, "image/jpeg", MediaSamples.face(), true);
        assertThat(derm).contains("\"facesBlurred\":0").contains("não executada");
    }

    @Test
    void uploadRulesAndOwnership() throws Exception {
        ApiClient c = verifiedDoctor();
        MockHttpServletResponse video = c.post("/api/v1/media/uploads", "{\"contentType\":\"video/mp4\",\"size\":1000}");
        assertThat(video.getStatus()).isEqualTo(422);
        assertThat(video.getContentAsString()).contains("Vídeo fica fora do MVP");
        assertThat(c.post("/api/v1/media/uploads", "{\"contentType\":\"image/png\",\"size\":999999999}").getStatus()).isEqualTo(422);

        Started s = requestUpload(c, "image/png", 100, false);
        MockHttpServletResponse early = c.post("/api/v1/media/" + s.id() + "/complete", null);
        assertThat(early.getStatus()).isEqualTo(409);
        assertThat(early.getContentAsString()).contains("upload-ausente");

        ApiClient other = verifiedDoctor();
        assertThat(other.get("/api/v1/media/" + s.id()).getStatus()).isEqualTo(404);
        assertThat(other.post("/api/v1/posts", """
                {"specialtyCode":"UTI","kind":"CASE","title":"Tentando usar imagem alheia","question":"?","mediaIds":["%s"]}
                """.formatted(s.id())).getStatus()).isEqualTo(422);

        ApiClient unverified = new ApiClient(mvc);
        unverified.register("semselo+" + System.nanoTime() + "@example.org", PASSWORD);
        unverified.activateEmail(mailer);
        assertThat(unverified.post("/api/v1/media/uploads", "{\"contentType\":\"image/png\",\"size\":100}").getStatus()).isEqualTo(403);
    }

    @Test
    void cleanupExpiresPendingUploadsAndDeletesOriginalsAfter24Hours() throws Exception {
        ApiClient c = verifiedDoctor();
        String review = uploadAndWait(c, "image/png", MediaSamples.png(60, 60), false);
        UUID waiting = UUID.fromString(group(ID, review));
        Started pending = requestUpload(c, "image/png", 100, false);

        jdbc.update("update media_asset set created_at = now() - interval '25 hours' where id = ?", waiting);
        jdbc.update("update media_asset set upload_expires_at = now() - interval '1 minute' where id = ?", pending.id());
        media.cleanup();

        MediaAsset w = assets.findById(waiting).orElseThrow();
        assertThat(w.getOriginalDeletedAt()).isNotNull();
        assertThat(objects.size(storage.buckets().mediaQuarantine(), w.getQuarantineKey())).isEmpty();
        assertThat(assets.findById(pending.id()).orElseThrow().getState()).isEqualTo(MediaAsset.State.EXPIRADO);

        // descartar apaga a prévia e encerra
        assertThat(c.delete("/api/v1/media/" + waiting).getStatus()).isEqualTo(204);
        assertThat(assets.findById(waiting).orElseThrow().getState()).isEqualTo(MediaAsset.State.DESCARTADO);
        assertThat(objects.size(storage.buckets().mediaQuarantine(), w.getPreviewKey())).isEmpty();
    }

    private static String group(Pattern p, String s) {
        Matcher m = p.matcher(s);
        assertThat(m.find()).as("%s em %s", p, s).isTrue();
        return m.group(1);
    }
}
