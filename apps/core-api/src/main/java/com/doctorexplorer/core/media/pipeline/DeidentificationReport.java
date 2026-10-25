package com.doctorexplorer.core.media.pipeline;

import java.util.List;

/**
 * O que a anonimização fez, mostrado ao autor na revisão (RF05, critério 5). Só nomes de campos e contagens,
 * nunca os valores removidos.
 */
public record DeidentificationReport(String detectedFormat, int width, int height, List<String> removedMetadata, boolean gpsRemoved,
        int exifOrientationApplied, List<String> dicomAttributesRemoved, int dicomUidsRemapped, boolean dicomBurnedInAnnotation,
        int textRegionsRedacted, String faceDetection, int facesBlurred, int manualRedactions, List<String> notes) {

    public DeidentificationReport withManual(int added) {
        return new DeidentificationReport(detectedFormat, width, height, removedMetadata, gpsRemoved, exifOrientationApplied, dicomAttributesRemoved,
                dicomUidsRemapped, dicomBurnedInAnnotation, textRegionsRedacted, faceDetection, facesBlurred, manualRedactions + added, notes);
    }
}
