package com.doctorexplorer.core.media.pipeline;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;

/**
 * Detecção de rosto embarcada (OpenCV, classificador Haar frontal) e cobertura irreversível: o retângulo do rosto
 * vira blocos grandes com a cor média de cada bloco (6 × 6 no máximo), sem os pixels originais. Nada sai do servidor.
 */
public final class FaceBlurrer {

    private static volatile CascadeClassifier classifier;

    private static CascadeClassifier classifier() {
        if (classifier == null) {
            synchronized (FaceBlurrer.class) {
                if (classifier == null) {
                    nu.pattern.OpenCV.loadLocally();
                    try (InputStream in = FaceBlurrer.class.getResourceAsStream("/media/haarcascade_frontalface_default.xml")) {
                        if (in == null) throw new IllegalStateException("classificador de rosto ausente");
                        Path tmp = Files.createTempFile("de-haar-", ".xml");
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                        CascadeClassifier c = new CascadeClassifier(tmp.toString());
                        Files.deleteIfExists(tmp);
                        if (c.empty()) throw new IllegalStateException("classificador de rosto não carregou");
                        classifier = c;
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        return classifier;
    }

    public List<Region> detect(BufferedImage img) {
        CascadeClassifier cascade = classifier(); // carrega a biblioteca nativa antes de qualquer Mat
        int w = img.getWidth(), h = img.getHeight();
        // reduz imagens grandes para a detecção (o retângulo volta para a escala original)
        double scale = Math.min(1.0, 1280.0 / Math.max(w, h));
        int sw = Math.max(1, (int) Math.round(w * scale)), sh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = small.createGraphics();
        g.drawImage(img, 0, 0, sw, sh, null);
        g.dispose();
        byte[] px = ((java.awt.image.DataBufferByte) small.getRaster().getDataBuffer()).getData();
        Mat gray = new Mat(sh, sw, CvType.CV_8UC1);
        gray.put(0, 0, px);
        org.opencv.imgproc.Imgproc.equalizeHist(gray, gray);
        MatOfRect faces = new MatOfRect();
        cascade.detectMultiScale(gray, faces, 1.1, 5, 0, new Size(24, 24), new Size());
        List<Region> out = new ArrayList<>();
        for (Rect r : faces.toArray()) {
            out.add(new Region((int) (r.x / scale), (int) (r.y / scale), (int) (r.width / scale), (int) (r.height / scale)).pad((int) (r.width / scale * 0.15)));
        }
        gray.release();
        faces.release();
        return out;
    }

    /** Pixelização grossa: cada bloco recebe a média, e o bloco tem no mínimo 1/6 do rosto. */
    public static void pixelate(BufferedImage img, List<Region> regions) {
        for (Region r0 : regions) {
            Region r = r0.clip(img.getWidth(), img.getHeight());
            if (r.w() <= 0 || r.h() <= 0) continue;
            int block = Math.max(8, Math.max(r.w(), r.h()) / 6);
            Graphics2D g = img.createGraphics();
            for (int by = r.y(); by < r.y() + r.h(); by += block) {
                for (int bx = r.x(); bx < r.x() + r.w(); bx += block) {
                    int bw = Math.min(block, r.x() + r.w() - bx), bh = Math.min(block, r.y() + r.h() - by);
                    long rs = 0, gs = 0, bs = 0;
                    for (int yy = by; yy < by + bh; yy++) {
                        for (int xx = bx; xx < bx + bw; xx++) {
                            int rgb = img.getRGB(xx, yy);
                            rs += (rgb >> 16) & 0xFF;
                            gs += (rgb >> 8) & 0xFF;
                            bs += rgb & 0xFF;
                        }
                    }
                    long n = (long) bw * bh;
                    g.setColor(new Color((int) (rs / n), (int) (gs / n), (int) (bs / n)));
                    g.fillRect(bx, by, bw, bh);
                }
            }
            g.dispose();
        }
    }
}
