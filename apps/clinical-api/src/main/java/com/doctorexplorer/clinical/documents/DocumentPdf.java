package com.doctorexplorer.clinical.documents;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.springframework.stereotype.Component;

/**
 * PDF/A-1b do documento clínico no formato do CFM (Res. 2.299/2021): médico com CRM/UF, paciente, data, itens, QR
 * code e endereço para a farmácia validar. Fonte embutida (DejaVu Sans), perfil de cor sRGB e metadados XMP; sem
 * transparência. A assinatura PAdES entra depois, por atualização incremental.
 */
@Component
public class DocumentPdf {

    public record Item(String title, List<String> details) {}

    public record Model(String heading, String doctorName, String crm, String uf, String patientName, LocalDate patientBirth, Instant issuedAt,
            List<Item> items, String indication, String notes, String validationCode, String validationUrl, List<String> footerNotes, boolean simulated) {}

    static final float MARGIN = 50;
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] render(Model m) {
        try (PDDocument doc = new PDDocument()) {
            PDFont regular = PDType0Font.load(doc, font("DejaVuSans.ttf"), true);
            PDFont bold = PDType0Font.load(doc, font("DejaVuSans-Bold.ttf"), true);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float width = page.getMediaBox().getWidth() - 2 * MARGIN;
            Writer w = new Writer(doc, page, regular, bold, width);

            w.text(bold, 16, m.heading());
            w.gap(6);
            w.text(bold, 11, m.doctorName());
            w.text(regular, 10, "CRM " + m.crm() + "/" + m.uf());
            w.gap(10);
            w.rule();
            w.text(regular, 10, "Paciente: " + m.patientName());
            w.text(regular, 10, "Nascimento: " + m.patientBirth().format(DATE));
            w.text(regular, 10, "Emitido em: " + DATE.format(m.issuedAt().atZone(java.time.ZoneId.of("America/Sao_Paulo"))));
            w.gap(6);
            w.rule();
            int n = 1;
            for (Item it : m.items()) {
                w.wrapped(bold, 11, n++ + ". " + it.title());
                for (String d : it.details()) w.wrapped(regular, 10, "   " + d);
                w.gap(6);
            }
            if (m.indication() != null && !m.indication().isBlank()) {
                w.wrapped(bold, 10, "Indicação clínica");
                w.wrapped(regular, 10, m.indication());
                w.gap(6);
            }
            if (m.notes() != null && !m.notes().isBlank()) {
                w.wrapped(bold, 10, "Observações");
                w.wrapped(regular, 10, m.notes());
            }

            // rodapé: QR + como validar
            float qr = 96;
            PDImageXObject img = LosslessFactory.createFromImage(doc, qr(m.validationUrl()));
            w.stream.drawImage(img, page.getMediaBox().getWidth() - MARGIN - qr, MARGIN, qr, qr);
            List<String> foot = new ArrayList<>();
            foot.add("Documento assinado digitalmente com certificado ICP-Brasil (PAdES).");
            foot.add("Validação: " + m.validationUrl());
            foot.add("Código: " + m.validationCode());
            foot.addAll(m.footerNotes());
            if (m.simulated()) foot.add("AMBIENTE DE TESTE: assinatura SIMULADA, sem validade jurídica.");
            float y = MARGIN + qr - 10;
            for (String line : foot) {
                for (String part : wrap(regular, 8, line, width - qr - 12)) {
                    w.at(regular, 8, MARGIN, y, part);
                    y -= 11;
                }
            }
            w.stream.close();

            metadata(doc, m);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // PDF/A-1 não aceita tabela de referências comprimida (XRef stream) nem object streams
            doc.save(out, CompressParameters.NO_COMPRESSION);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar o PDF/A", e);
        }
    }

    private static void metadata(PDDocument doc, Model m) throws Exception {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(m.issuedAt().toEpochMilli());
        String title = m.heading() + " · " + m.validationCode();
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle(title);
        info.setAuthor(m.doctorName());
        info.setProducer("Doctor Explorer");
        info.setCreator("Doctor Explorer");
        info.setCreationDate(cal);
        info.setModificationDate(cal);

        XMPMetadata xmp = XMPMetadata.createXMPMetadata();
        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        dc.setTitle(title);
        dc.addCreator(m.doctorName());
        PDFAIdentificationSchema id = xmp.createAndAddPDFAIdentificationSchema();
        id.setPart(1);
        id.setConformance("B");
        XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
        basic.setCreateDate(cal);
        basic.setModifyDate(cal);
        basic.setCreatorTool("Doctor Explorer");
        AdobePDFSchema pdf = xmp.createAndAddAdobePDFSchema();
        pdf.setProducer("Doctor Explorer");
        ByteArrayOutputStream x = new ByteArrayOutputStream();
        new XmpSerializer().serialize(xmp, x, true);
        PDMetadata meta = new PDMetadata(doc);
        meta.importXMPMetadata(x.toByteArray());
        doc.getDocumentCatalog().setMetadata(meta);

        PDOutputIntent intent = new PDOutputIntent(doc, new ByteArrayInputStream(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData()));
        intent.setInfo("sRGB IEC61966-2.1");
        intent.setOutputCondition("sRGB IEC61966-2.1");
        intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
        intent.setRegistryName("http://www.color.org");
        doc.getDocumentCatalog().addOutputIntent(intent);
    }

    static BufferedImage qr(String url) throws Exception {
        BitMatrix bits = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 200, 200, Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 1));
        BufferedImage img = new BufferedImage(bits.getWidth(), bits.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < bits.getHeight(); y++) for (int x = 0; x < bits.getWidth(); x++) img.setRGB(x, y, bits.get(x, y) ? 0x000000 : 0xFFFFFF);
        return img;
    }

    private static InputStream font(String name) {
        InputStream in = DocumentPdf.class.getResourceAsStream("/fonts/" + name);
        if (in == null) throw new IllegalStateException("fonte ausente: " + name);
        return in;
    }

    static List<String> wrap(PDFont font, float size, String text, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    /** Escreve de cima para baixo; o texto do corpo nunca invade o rodapé. */
    private static final class Writer {
        final PDPageContentStream stream;
        final float width;
        float y;

        Writer(PDDocument doc, PDPage page, PDFont regular, PDFont bold, float width) throws IOException {
            this.stream = new PDPageContentStream(doc, page);
            this.width = width;
            this.y = page.getMediaBox().getHeight() - MARGIN;
        }

        void text(PDFont font, float size, String s) throws IOException {
            y -= size + 4;
            if (y < MARGIN + 120) throw new IllegalStateException("conteúdo não cabe em uma página");
            at(font, size, MARGIN, y, s);
        }

        void wrapped(PDFont font, float size, String s) throws IOException {
            for (String line : wrap(font, size, s, width)) text(font, size, line);
        }

        void at(PDFont font, float size, float x, float yy, String s) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, yy);
            stream.showText(s);
            stream.endText();
        }

        void gap(float g) {
            y -= g;
        }

        void rule() throws IOException {
            y -= 4;
            stream.setStrokingColor(0.7f, 0.7f, 0.7f);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + width, y);
            stream.stroke();
            y -= 4;
        }
    }
}
