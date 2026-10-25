package com.doctorexplorer.core.media.pipeline;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.deident.DeIdentifier;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;

/**
 * DICOM (RF05, critério 3): perfil básico de confidencialidade da PS3.15 Anexo E pelo dcm4che (sem opções de
 * retenção: datas, aparelho, instituição e UIDs saem), UIDs novos e o quadro 0 renderizado para PNG. O relatório
 * lista os atributos removidos ou substituídos (só os nomes, nunca os valores).
 */
public final class DicomDeidentifier {

    public record Result(BufferedImage image, List<String> changedAttributes, int uidsRemapped, boolean burnedInAnnotation) {}

    private final long maxPixels;

    public DicomDeidentifier(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public Result deidentify(byte[] bytes) {
        Attributes fmi;
        Attributes ds;
        try (DicomInputStream in = new DicomInputStream(new ByteArrayInputStream(bytes))) {
            fmi = in.readFileMetaInformation();
            ds = in.readDataset();
        } catch (IOException | RuntimeException e) {
            throw new MediaRejected("DICOM ilegível ou corrompido.");
        }
        long pixels = (long) ds.getInt(Tag.Rows, 0) * ds.getInt(Tag.Columns, 0) * Math.max(1, ds.getInt(Tag.NumberOfFrames, 1));
        if (pixels <= 0 || pixels > maxPixels) throw new MediaRejected("DICOM sem imagem ou grande demais (" + pixels / 1_000_000 + " MP).");
        boolean burnedIn = "YES".equalsIgnoreCase(ds.getString(Tag.BurnedInAnnotation, ""));

        Attributes before = new Attributes(ds);
        new DeIdentifier().deidentify(ds);
        TreeSet<String> changed = new TreeSet<>();
        int uids = 0;
        for (int tag : before.tags()) {
            if (tag == Tag.PixelData) continue;
            Object a = before.getValue(tag);
            Object b = ds.containsValue(tag) ? ds.getValue(tag) : null;
            boolean same = b != null && Objects.deepEquals(a instanceof byte[] ? a : String.valueOf(before.getString(tag)), b instanceof byte[] ? b : String.valueOf(ds.getString(tag)));
            if (!same) {
                String kw = ElementDictionary.keywordOf(tag, null);
                changed.add(kw == null || kw.isEmpty() ? String.format("(%04X,%04X)", tag >>> 16, tag & 0xFFFF) : kw);
                if (before.getVR(tag) == org.dcm4che3.data.VR.UI) uids++;
            }
        }
        // meta-informação do arquivo acompanha o novo SOP Instance UID
        Attributes newFmi = ds.createFileMetaInformation(fmi.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian));
        byte[] clean;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DicomOutputStream out = new DicomOutputStream(bos, UID.ExplicitVRLittleEndian)) {
            out.writeDataset(newFmi, ds);
            out.flush();
            clean = bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("falha ao regravar o DICOM desidentificado", e);
        }
        return new Result(render(clean), new ArrayList<>(changed), uids, burnedIn);
    }

    private static BufferedImage render(byte[] dicom) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(dicom))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext()) throw new IllegalStateException("leitor DICOM indisponível");
            ImageReader r = readers.next();
            try {
                r.setInput(in);
                BufferedImage frame = r.read(0, r.getDefaultReadParam());
                return ImageDecoder.toRgb(frame);
            } finally {
                r.dispose();
            }
        } catch (IOException e) {
            throw new MediaRejected("Não foi possível exibir este DICOM (sintaxe de transferência sem suporte).");
        }
    }
}
