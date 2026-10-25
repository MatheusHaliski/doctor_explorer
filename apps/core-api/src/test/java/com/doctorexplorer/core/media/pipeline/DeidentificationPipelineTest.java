package com.doctorexplorer.core.media.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.doctorexplorer.core.media.MediaSamples;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RF05 · pipeline real (Tesseract, libheif, OpenCV e dcm4che), sem mocks: o que sai não pode carregar identificador. */
class DeidentificationPipelineTest {

    private static final long MAX_PIXELS = 40_000_000L;

    private final DeidentificationPipeline pipeline = new DeidentificationPipeline(new ImageDecoder(MAX_PIXELS, "heif-convert", "heif-info"),
            new DicomDeidentifier(MAX_PIXELS), new OcrRedactor("tesseract", "por+eng", 30), new FaceBlurrer());

    @Test
    void sniffsFormatFromBytesNotFromName() {
        assertThat(MediaFormat.sniff(MediaSamples.jpegWithExif(1))).contains(MediaFormat.JPEG);
        assertThat(MediaFormat.sniff(MediaSamples.png(4, 4))).contains(MediaFormat.PNG);
        assertThat(MediaFormat.sniff(MediaSamples.dicom())).contains(MediaFormat.DICOM);
        assertThat(MediaFormat.sniff("<html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(MediaFormat.HEIC.matchesDeclared("image/heif")).isTrue();
    }

    @Test
    void rejectsContentThatDoesNotMatchTheDeclaredType() {
        assertThatThrownBy(() -> pipeline.run(MediaSamples.png(10, 10), "image/jpeg", true)).isInstanceOf(MediaRejected.class)
                .hasMessageContaining("image/png");
        assertThatThrownBy(() -> pipeline.run(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'}, "image/heic", true))
                .isInstanceOf(MediaRejected.class).hasMessageContaining("vídeo");
    }

    @Test
    void rejectsImagesAboveThePixelLimitBeforeDecoding() {
        ImageDecoder small = new ImageDecoder(10_000, "heif-convert", "heif-info");
        assertThatThrownBy(() -> small.decode(MediaSamples.png(200, 100), MediaFormat.PNG)).isInstanceOf(MediaRejected.class)
                .hasMessageContaining("grande demais");
    }

    @Test
    void stripsExifAndGpsAndAppliesOrientationToThePixels() throws Exception {
        DeidentificationPipeline.Output out = pipeline.run(MediaSamples.jpegWithExif(6), "image/jpeg", false);

        assertThat(out.contentType()).isEqualTo("image/jpeg");
        assertThat(out.report().gpsRemoved()).isTrue();
        assertThat(out.report().exifOrientationApplied()).isEqualTo(6);
        assertThat(out.report().removedMetadata()).anyMatch(s -> s.startsWith("Exif IFD0")).anyMatch(s -> s.startsWith("GPS"));
        // rotação de 90° no sentido horário: 400×200 vira 200×400 e o canto vermelho vai para o canto superior direito
        assertThat(out.width()).isEqualTo(200);
        assertThat(out.height()).isEqualTo(400);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out.bytes()));
        assertThat(red(img.getRGB(190, 10))).isGreaterThan(200);
        assertThat(red(img.getRGB(10, 10))).isLessThan(120);

        Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(out.bytes()));
        List<String> dirs = StreamSupport.stream(md.getDirectories().spliterator(), false).map(Directory::getName).toList();
        assertThat(dirs).noneMatch(d -> d.startsWith("Exif") || d.startsWith("GPS") || d.startsWith("XMP") || d.startsWith("IPTC"));
        assertThat(new String(out.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("CameraExemplo");
    }

    @Test
    void everyOrientationMatchesTheExifDefinition() {
        BufferedImage src = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) src.setRGB(x, y, y * 3 + x + 1);
        // pixel (0,0) de saída para cada orientação: o canto da origem que a câmera mostraria no topo à esquerda
        int[] topLeft = {0, 1, 3, 6, 4, 1, 4, 6, 3};
        for (int o = 1; o <= 8; o++) {
            BufferedImage r = ImageDecoder.orient(src, o);
            assertThat(r.getWidth()).as("largura o=%d", o).isEqualTo(o >= 5 ? 2 : 3);
            assertThat(r.getRGB(0, 0) & 0xFFFFFF).as("o=%d", o).isEqualTo(topLeft[o]);
        }
    }

    @Test
    void blacksOutBurnedInTextFoundByOcr() throws Exception {
        DeidentificationPipeline.Output out = pipeline.run(MediaSamples.pngWithText(), "image/png", false);

        assertThat(out.contentType()).isEqualTo("image/png");
        assertThat(out.report().textRegionsRedacted()).isGreaterThanOrEqualTo(2);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out.bytes()));
        // o meio das duas linhas de identificador virou tarja preta; a área clínica (círculo verde) ficou intacta
        assertThat(img.getRGB(200, 75) & 0xFFFFFF).isZero();
        assertThat(img.getRGB(200, 165) & 0xFFFFFF).isZero();
        assertThat((img.getRGB(750, 290) >> 8) & 0xFF).isGreaterThan(120);
        assertThat(new OcrRedactor("tesseract", "por+eng", 30).findText(img)).isEmpty();
    }

    @Test
    void pixelatesFacesUnlessTheDermatologyAuthorOptsOut() throws Exception {
        assertThat(new FaceBlurrer().detect(ImageIO.read(new ByteArrayInputStream(MediaSamples.face())))).isNotEmpty();

        DeidentificationPipeline.Output blurred = pipeline.run(MediaSamples.face(), "image/jpeg", true);
        assertThat(blurred.report().facesBlurred()).isGreaterThanOrEqualTo(1);
        assertThat(blurred.report().faceDetection()).isEqualTo("executada");
        assertThat(new FaceBlurrer().detect(ImageIO.read(new ByteArrayInputStream(blurred.bytes())))).isEmpty();

        DeidentificationPipeline.Output kept = pipeline.run(MediaSamples.face(), "image/jpeg", false);
        assertThat(kept.report().facesBlurred()).isZero();
        assertThat(kept.report().faceDetection()).startsWith("não executada");
        assertThat(kept.report().notes()).anyMatch(n -> n.contains("tarja manual"));
    }

    @Test
    void deidentifiesDicomWithTheBasicProfileAndExportsPixelsOnly() {
        DeidentificationPipeline.Output out = pipeline.run(MediaSamples.dicom(), "application/dicom", true);

        assertThat(out.contentType()).isEqualTo("image/png");
        assertThat(out.width()).isEqualTo(64);
        assertThat(out.report().dicomAttributesRemoved()).contains("PatientName", "PatientID", "PatientBirthDate", "InstitutionName");
        assertThat(out.report().dicomUidsRemapped()).isGreaterThanOrEqualTo(3);
        assertThat(new String(out.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("SILVA").doesNotContain("483920");
    }

    @Test
    void convertsHeicThroughLibheif(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/heif-enc")), "heif-enc ausente");
        Path png = dir.resolve("in.png");
        Path heic = dir.resolve("in.heic");
        Files.write(png, MediaSamples.png(320, 240));
        Tools.run(List.of("heif-enc", png.toString(), "-o", heic.toString()), null, 60);

        DeidentificationPipeline.Output out = pipeline.run(Files.readAllBytes(heic), "image/heic", false);
        assertThat(out.contentType()).isEqualTo("image/jpeg");
        assertThat(out.width()).isEqualTo(320);
        assertThat(out.height()).isEqualTo(240);
        assertThat(out.report().detectedFormat()).isEqualTo("HEIC");
    }

    @Test
    void manualRedactionCoversTheChosenArea() throws Exception {
        byte[] png = MediaSamples.png(100, 100);
        byte[] out = DeidentificationPipeline.applyManual(png, "image/png", List.of(new Region(10, 10, 20, 20)));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out));
        assertThat(img.getRGB(15, 15) & 0xFFFFFF).isZero();
        assertThat(img.getRGB(50, 50) & 0xFFFFFF).isNotZero();
    }

    @Test
    void failsClosedWhenAToolIsMissing() {
        DeidentificationPipeline broken = new DeidentificationPipeline(new ImageDecoder(MAX_PIXELS, "heif-convert", "heif-info"),
                new DicomDeidentifier(MAX_PIXELS), new OcrRedactor("/nao/existe/tesseract", "por", 30), new FaceBlurrer());
        assertThatThrownBy(() -> broken.run(MediaSamples.png(50, 50), "image/png", true)).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(MediaRejected.class);
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }
}
