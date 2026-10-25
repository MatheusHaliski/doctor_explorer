package com.doctorexplorer.core.media;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;

/** Arquivos sintéticos para os testes do RF05 (nenhum dado real de paciente). */
public final class MediaSamples {

    public static final String PATIENT = "SILVA^MARIA";

    private MediaSamples() {}

    /** Foto 400×200 com quadrante vermelho no canto superior esquerdo, EXIF com câmera, orientação e GPS. */
    public static byte[] jpegWithExif(int orientation) {
        BufferedImage img = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 90, 200));
        g.fillRect(0, 0, 400, 200);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 50);
        g.dispose();
        byte[] plain = encode(img, "jpg");
        try {
            TiffOutputSet set = new TiffOutputSet();
            TiffOutputDirectory root = set.getOrCreateRootDirectory();
            root.add(TiffTagConstants.TIFF_TAG_MAKE, "CameraExemplo");
            root.add(TiffTagConstants.TIFF_TAG_MODEL, "Modelo 1");
            root.add(TiffTagConstants.TIFF_TAG_ORIENTATION, (short) orientation);
            set.setGpsInDegrees(-46.633308, -23.550520);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ExifRewriter().updateExifMetadataLossless(plain, out, set);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "Laudo" fotografado: identificadores impressos em letras grandes sobre fundo claro, com área clínica ao lado. */
    public static byte[] pngWithText() {
        BufferedImage img = new BufferedImage(900, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 900, 360);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        g.drawString("PACIENTE MARIA SILVA", 40, 90);
        g.drawString("PRONTUARIO 483920", 40, 180);
        g.setColor(new Color(0, 160, 80));
        g.fillOval(700, 240, 100, 100);
        g.dispose();
        return encode(img, "png");
    }

    public static byte[] png(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 180, 160));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return encode(img, "png");
    }

    /** Retrato de domínio público (NASA) para a detecção de rosto. */
    public static byte[] face() {
        try (InputStream in = MediaSamples.class.getResourceAsStream("/media/astronaut.jpg")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Captura secundária 64×64, 8 bits, com nome, prontuário, nascimento, instituição e UIDs. */
    public static byte[] dicom() {
        Attributes ds = new Attributes();
        ds.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        ds.setString(Tag.SOPInstanceUID, VR.UI, "1.2.826.0.1.3680043.2.1125.9.1");
        ds.setString(Tag.StudyInstanceUID, VR.UI, "1.2.826.0.1.3680043.2.1125.9.2");
        ds.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.826.0.1.3680043.2.1125.9.3");
        ds.setString(Tag.PatientName, VR.PN, PATIENT);
        ds.setString(Tag.PatientID, VR.LO, "483920");
        ds.setString(Tag.PatientBirthDate, VR.DA, "19800101");
        ds.setString(Tag.InstitutionName, VR.LO, "Hospital Exemplo");
        ds.setString(Tag.StudyDate, VR.DA, "20260101");
        ds.setString(Tag.Modality, VR.CS, "OT");
        ds.setInt(Tag.Rows, VR.US, 64);
        ds.setInt(Tag.Columns, VR.US, 64);
        ds.setInt(Tag.SamplesPerPixel, VR.US, 1);
        ds.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        ds.setInt(Tag.BitsAllocated, VR.US, 8);
        ds.setInt(Tag.BitsStored, VR.US, 8);
        ds.setInt(Tag.HighBit, VR.US, 7);
        ds.setInt(Tag.PixelRepresentation, VR.US, 0);
        byte[] px = new byte[64 * 64];
        for (int i = 0; i < px.length; i++) px[i] = (byte) ((i % 64) * 4);
        ds.setBytes(Tag.PixelData, VR.OB, px);
        Attributes fmi = ds.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DicomOutputStream out = new DicomOutputStream(bos, UID.ExplicitVRLittleEndian)) {
            out.writeDataset(fmi, ds);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encode(BufferedImage img, String format) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, format, bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
