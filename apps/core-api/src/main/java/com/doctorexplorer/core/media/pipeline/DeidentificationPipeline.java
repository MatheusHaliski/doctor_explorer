package com.doctorexplorer.core.media.pipeline;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * RF05 · valida → (DICOM: perfil básico) → remove metadados → detecta texto (OCR) e rostos → tarja e pixelização. Tudo em memória; a saída é
 * reconstruída só dos pixels (JPEG para fotos, PNG para PNG e DICOM). Qualquer falha de ferramenta interrompe: a
 * mídia vai para FALHOU e nada é publicado.
 */
public final class DeidentificationPipeline {

    public record Output(byte[] bytes, String contentType, int width, int height, DeidentificationReport report) {}

    private final ImageDecoder images;
    private final DicomDeidentifier dicom;
    private final OcrRedactor ocr;
    private final FaceBlurrer faces;

    public DeidentificationPipeline(ImageDecoder images, DicomDeidentifier dicom, OcrRedactor ocr, FaceBlurrer faces) {
        this.images = images;
        this.dicom = dicom;
        this.ocr = ocr;
        this.faces = faces;
    }

    public Output run(byte[] bytes, String declaredType, boolean blurFaces) {
        MediaFormat format = MediaFormat.sniff(bytes).orElseThrow(() ->
                new MediaRejected("Formato não aceito. Envie JPEG, PNG, HEIC ou DICOM (vídeo fica fora do MVP)."));
        if (!format.matchesDeclared(declaredType)) {
            throw new MediaRejected("O conteúdo do arquivo é " + format.mime + ", mas foi declarado como " + declaredType + ".");
        }
        List<String> notes = new ArrayList<>();
        BufferedImage img;
        List<String> removed = List.of();
        boolean gps = false;
        int orientation = 1;
        List<String> dicomRemoved = List.of();
        int uids = 0;
        boolean burnedIn = false;
        if (format == MediaFormat.DICOM) {
            DicomDeidentifier.Result r = dicom.deidentify(bytes);
            img = r.image();
            dicomRemoved = r.changedAttributes();
            uids = r.uidsRemapped();
            burnedIn = r.burnedInAnnotation();
            notes.add("DICOM desidentificado pelo perfil básico da PS3.15 Anexo E e exportado como PNG para exibição.");
        } else {
            ImageDecoder.Decoded d = images.decode(bytes, format);
            img = d.image();
            removed = d.removedMetadata();
            gps = d.hadGps();
            orientation = d.orientation();
        }

        // as duas detecções olham a imagem intacta: uma tarja de OCR sobre a testa não pode esconder o rosto do detector
        List<Region> text = ocr.findText(img);
        String faceMode;
        List<Region> found = List.of();
        if (blurFaces) {
            found = faces.detect(img);
            faceMode = "executada";
        } else {
            faceMode = "não executada: imagem dermatológica sem escolha do autor";
            notes.add("Se houver rosto visível, cubra com a tarja manual antes de aprovar.");
        }
        FaceBlurrer.pixelate(img, found);
        OcrRedactor.blackout(img, text);
        int blurred = found.size();

        boolean photo = format == MediaFormat.JPEG || format == MediaFormat.HEIC;
        byte[] out = encode(img, photo);
        DeidentificationReport report = new DeidentificationReport(format.name(), img.getWidth(), img.getHeight(), removed, gps, orientation,
                dicomRemoved, uids, burnedIn, text.size(), faceMode, blurred, 0, notes);
        return new Output(out, photo ? "image/jpeg" : "image/png", img.getWidth(), img.getHeight(), report);
    }

    /** Tarjas manuais do autor sobre a prévia atual (coordenadas em pixels). */
    public static byte[] applyManual(byte[] current, String contentType, List<Region> regions) {
        try {
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(current));
            if (img == null) throw new IllegalStateException("prévia ilegível");
            BufferedImage rgb = ImageDecoder.toRgb(img);
            OcrRedactor.blackout(rgb, regions);
            return encode(rgb, "image/jpeg".equals(contentType));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] encode(BufferedImage img, boolean jpeg) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (jpeg) {
                ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
                ImageWriteParam p = w.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(0.9f);
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                    w.setOutput(ios);
                    w.write(null, new IIOImage(img, null, null), p); // sem metadados
                } finally {
                    w.dispose();
                }
            } else {
                ImageIO.write(img, "png", bos);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
