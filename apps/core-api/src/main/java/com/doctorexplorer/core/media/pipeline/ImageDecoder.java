package com.doctorexplorer.core.media.pipeline;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * JPEG, PNG e HEIC → imagem RGB sem nenhum metadado. Confere o tamanho em pixels pelo cabeçalho <b>antes</b> de
 * decodificar (defesa contra "bomba de descompressão"), aplica a orientação EXIF e lista o que existia de
 * metadado para o relatório. O metadado nunca é copiado: a saída é reconstruída só a partir dos pixels.
 */
public final class ImageDecoder {

    /** Diretórios do metadata-extractor que só descrevem a estrutura do arquivo (não são metadado do autor). */
    private static final Set<String> STRUCTURAL = Set.of("JPEG", "JFIF", "Huffman", "File Type", "PNG-IHDR", "PNG-pHYs", "PNG-gAMA",
            "PNG-sRGB", "PNG-cHRM", "PNG-bKGD", "HEIF", "Exif Thumbnail");

    public record Decoded(BufferedImage image, List<String> removedMetadata, boolean hadGps, int orientation) {}

    private final long maxPixels;
    private final String heifConvert;
    private final String heifInfo;

    public ImageDecoder(long maxPixels, String heifConvert, String heifInfo) {
        this.maxPixels = maxPixels;
        this.heifConvert = heifConvert;
        this.heifInfo = heifInfo;
    }

    public Decoded decode(byte[] bytes, MediaFormat format) {
        List<String> removed = new ArrayList<>();
        boolean gps = false;
        int orientation = 1;
        try {
            Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            Set<String> names = new LinkedHashSet<>();
            for (Directory d : md.getDirectories()) {
                if (STRUCTURAL.contains(d.getName())) continue;
                if (d.getName().startsWith("GPS")) gps = true;
                names.add(d.getName() + " (" + d.getTagCount() + " campos)");
            }
            removed.addAll(names);
            ExifIFD0Directory ifd0 = md.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null && ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) orientation = ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        } catch (Exception e) {
            // metadado ilegível não impede: a saída é reconstruída só dos pixels de qualquer forma
            removed.add("metadados ilegíveis descartados");
        }
        BufferedImage img = format == MediaFormat.HEIC ? decodeHeic(bytes) : decodeImageIo(bytes);
        return new Decoded(toRgb(orient(img, orientation)), removed, gps, orientation);
    }

    private BufferedImage decodeImageIo(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new MediaRejected("Imagem ilegível ou corrompida.");
            ImageReader r = readers.next();
            try {
                r.setInput(in, true, true); // ignora metadados na leitura
                checkPixels((long) r.getWidth(0) * r.getHeight(0));
                return r.read(0);
            } finally {
                r.dispose();
            }
        } catch (IOException e) {
            throw new MediaRejected("Imagem ilegível ou corrompida.");
        }
    }

    private static final Pattern HEIF_SIZE = Pattern.compile("(\\d+)x(\\d+)");

    private BufferedImage decodeHeic(byte[] bytes) {
        Path dir = null;
        try {
            dir = Tools.privateTempDir();
            Path in = dir.resolve("in.heic");
            Path out = dir.resolve("out.png");
            Files.write(in, bytes);
            String info = new String(Tools.run(List.of(heifInfo, in.toString()), null, 30), StandardCharsets.UTF_8);
            Matcher m = HEIF_SIZE.matcher(info);
            if (!m.find()) throw new MediaRejected("Não foi possível ler as dimensões do HEIC.");
            checkPixels(Long.parseLong(m.group(1)) * Long.parseLong(m.group(2)));
            Tools.run(List.of(heifConvert, in.toString(), out.toString()), null, 60);
            BufferedImage img = ImageIO.read(out.toFile());
            if (img == null) throw new MediaRejected("HEIC ilegível.");
            return img;
        } catch (IOException e) {
            throw new IllegalStateException("conversão HEIC falhou", e);
        } finally {
            Tools.deleteTree(dir);
        }
    }

    void checkPixels(long pixels) {
        if (pixels <= 0 || pixels > maxPixels) {
            throw new MediaRejected("Imagem grande demais: " + pixels / 1_000_000 + " MP (limite " + maxPixels / 1_000_000 + " MP). Reduza a resolução e envie de novo.");
        }
    }

    /** Orientação EXIF 1–8 → os mesmos pixels na posição em que a câmera os mostraria (mapeamento explícito). */
    static BufferedImage orient(BufferedImage src, int o) {
        if (o <= 1 || o > 8) return src;
        BufferedImage rgb = toRgb(src);
        int w = rgb.getWidth(), h = rgb.getHeight();
        boolean swap = o >= 5;
        int dw = swap ? h : w, dh = swap ? w : h;
        int[] in = rgb.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[in.length];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int sx, sy;
                switch (o) {
                    case 2 -> { sx = w - 1 - x; sy = y; }
                    case 3 -> { sx = w - 1 - x; sy = h - 1 - y; }
                    case 4 -> { sx = x; sy = h - 1 - y; }
                    case 5 -> { sx = y; sy = x; }
                    case 6 -> { sx = y; sy = h - 1 - x; }
                    case 7 -> { sx = w - 1 - y; sy = h - 1 - x; }
                    default -> { sx = w - 1 - y; sy = x; } // 8
                }
                out[y * dw + x] = in[sy * w + sx];
            }
        }
        BufferedImage dst = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        dst.setRGB(0, 0, dw, dh, out, 0, dw);
        return dst;
    }

    /** Copia só os pixels para um RGB novo (transparência vira branco). */
    public static BufferedImage toRgb(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, dst.getWidth(), dst.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }
}
