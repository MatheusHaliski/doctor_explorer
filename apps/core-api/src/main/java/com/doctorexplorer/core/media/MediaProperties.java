package com.doctorexplorer.core.media;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * doctor-explorer.media.*: limites e ferramentas da anonimização (RF05). Tesseract e libheif são binários do
 * sistema (imagem base em infra/images/core-api-base); se faltarem, o processamento falha fechado.
 */
@ConfigurationProperties("doctor-explorer.media")
public record MediaProperties(Long maxImageBytes, Long maxDicomBytes, Long maxPixels, String tesseract, String heifConvert,
        String heifInfo, String ocrLanguages, Integer ocrMinConfidence, List<String> corsOrigins) {

    public MediaProperties {
        if (maxImageBytes == null) maxImageBytes = 20L * 1024 * 1024;
        if (maxDicomBytes == null) maxDicomBytes = 50L * 1024 * 1024;
        if (maxPixels == null) maxPixels = 40_000_000L;
        if (tesseract == null || tesseract.isBlank()) tesseract = "tesseract";
        if (heifConvert == null || heifConvert.isBlank()) heifConvert = "heif-convert";
        if (heifInfo == null || heifInfo.isBlank()) heifInfo = "heif-info";
        if (ocrLanguages == null || ocrLanguages.isBlank()) ocrLanguages = "por+eng";
        if (ocrMinConfidence == null) ocrMinConfidence = 30;
        if (corsOrigins == null) corsOrigins = List.of();
    }
}
