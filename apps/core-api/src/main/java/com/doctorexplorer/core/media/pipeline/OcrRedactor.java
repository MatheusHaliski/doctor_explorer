package com.doctorexplorer.core.media.pipeline;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * OCR (Tesseract, português + inglês) procura texto gravado na imagem e cobre cada linha com tarja preta. A regra é
 * cobrir <b>todo</b> texto legível com 2+ caracteres (nome, data, prontuário, carimbo): é mais seguro tarjar um
 * rótulo inofensivo do que deixar um identificador passar. O autor pode cobrir mais, nunca descobrir.
 */
public final class OcrRedactor {

    private final String tesseract;
    private final String languages;
    private final int minConfidence;

    public OcrRedactor(String tesseract, String languages, int minConfidence) {
        this.tesseract = tesseract;
        this.languages = languages;
        this.minConfidence = minConfidence;
    }

    public List<Region> findText(BufferedImage img) {
        byte[] png;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            png = bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // psm 11: texto esparso em qualquer lugar da imagem (laudos fotografados, carimbos, anotações)
        String tsv = new String(Tools.run(List.of(tesseract, "stdin", "stdout", "-l", languages, "--psm", "11", "tsv"), png, 120), StandardCharsets.UTF_8);
        Map<String, int[]> lines = new LinkedHashMap<>(); // bloco/parágrafo/linha → x0,y0,x1,y1
        for (String row : tsv.lines().skip(1).toList()) {
            String[] c = row.split("\t", -1);
            if (c.length < 12 || !"5".equals(c[0])) continue; // nível 5 = palavra
            String text = c[11].strip();
            double conf;
            try {
                conf = Double.parseDouble(c[10]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (conf < minConfidence || text.replaceAll("[^\\p{L}\\p{N}]", "").length() < 2) continue;
            int x = Integer.parseInt(c[6]), y = Integer.parseInt(c[7]), w = Integer.parseInt(c[8]), h = Integer.parseInt(c[9]);
            lines.merge(c[2] + "/" + c[3] + "/" + c[4], new int[] {x, y, x + w, y + h},
                    (a, b) -> new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])});
        }
        List<Region> out = new ArrayList<>();
        for (int[] b : lines.values()) out.add(new Region(b[0], b[1], b[2] - b[0], b[3] - b[1]).pad(Math.max(4, (b[3] - b[1]) / 4)));
        return out;
    }

    public static void blackout(BufferedImage img, List<Region> regions) {
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        for (Region r : regions) {
            Region c = r.clip(img.getWidth(), img.getHeight());
            if (c.w() > 0 && c.h() > 0) g.fillRect(c.x(), c.y(), c.w(), c.h());
        }
        g.dispose();
    }
}
