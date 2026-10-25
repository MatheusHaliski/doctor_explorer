package com.doctorexplorer.core.media.pipeline;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** Formato pelos bytes (assinatura), nunca pela extensão ou pelo Content-Type declarado (RF05, critério 2). */
public enum MediaFormat {
    JPEG("image/jpeg"), PNG("image/png"), HEIC("image/heic"), DICOM("application/dicom");

    public final String mime;

    MediaFormat(String mime) {
        this.mime = mime;
    }

    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1");

    public static Optional<MediaFormat> sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Optional.of(JPEG);
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return Optional.of(PNG);
        if (b.length >= 12 && "ftyp".equals(new String(b, 4, 4, StandardCharsets.US_ASCII))
                && HEIF_BRANDS.contains(new String(b, 8, 4, StandardCharsets.US_ASCII))) return Optional.of(HEIC);
        if (b.length >= 132 && "DICM".equals(new String(b, 128, 4, StandardCharsets.US_ASCII))) return Optional.of(DICOM);
        return Optional.empty();
    }

    /** O tipo declarado no pedido de upload precisa bater com o detectado (image/heif vale como HEIC). */
    public boolean matchesDeclared(String declared) {
        String d = declared == null ? "" : declared.toLowerCase();
        return d.equals(mime) || (this == HEIC && d.equals("image/heif")) || (this == DICOM && d.equals("application/dicom"));
    }
}
