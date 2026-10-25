import { describe, expect, it } from "vitest";
import { contentTypeFor, describeReport, type MediaReport } from "@/lib/media";

const base: MediaReport = {
  detectedFormat: "JPEG",
  width: 10,
  height: 10,
  removedMetadata: ["Exif IFD0 (3 campos)", "GPS (4 campos)"],
  gpsRemoved: true,
  exifOrientationApplied: 6,
  dicomAttributesRemoved: [],
  dicomUidsRemapped: 0,
  dicomBurnedInAnnotation: false,
  textRegionsRedacted: 2,
  faceDetection: "executada",
  facesBlurred: 1,
  manualRedactions: 0,
  notes: [],
};

describe("contentTypeFor", () => {
  it("usa a extensão quando o navegador não sabe o tipo", () => {
    expect(contentTypeFor({ name: "IMG_0001.HEIC", type: "" })).toBe("image/heic");
    expect(contentTypeFor({ name: "tc.dcm", type: "application/octet-stream" })).toBe("application/dicom");
    expect(contentTypeFor({ name: "foto.jpg", type: "" })).toBe("image/jpeg");
    expect(contentTypeFor({ name: "x.png", type: "image/png" })).toBe("image/png");
  });
});

describe("describeReport", () => {
  it("resume o que a anonimização fez, sem valores", () => {
    const lines = describeReport(base);
    expect(lines).toContain("Localização (GPS) removida.");
    expect(lines).toContain("2 trechos de texto cobertos com tarja preta.");
    expect(lines).toContain("1 rosto borrado.");
  });

  it("explica quando a detecção de rosto não rodou e quando é DICOM", () => {
    expect(describeReport({ ...base, faceDetection: "não executada", facesBlurred: 0 })).toContain("Rostos não foram borrados (imagem dermatológica).");
    const dicom = describeReport({ ...base, detectedFormat: "DICOM", dicomAttributesRemoved: ["PatientName", "PatientID"], dicomUidsRemapped: 3, textRegionsRedacted: 0 });
    expect(dicom[0]).toBe("DICOM desidentificado: 2 campos alterados (PatientName, PatientID).");
    expect(dicom).toContain("Nenhum texto encontrado na imagem.");
    expect(describeReport(null)).toEqual([]);
  });
});
