/** RF05 · utilidades da mídia de casos (sem estado, testadas em tests/media.test.ts). */

export type MediaReport = {
  detectedFormat: string;
  width: number;
  height: number;
  removedMetadata: string[];
  gpsRemoved: boolean;
  exifOrientationApplied: number;
  dicomAttributesRemoved: string[];
  dicomUidsRemapped: number;
  dicomBurnedInAnnotation: boolean;
  textRegionsRedacted: number;
  faceDetection: string;
  facesBlurred: number;
  manualRedactions: number;
  notes: string[];
};

/** Navegadores deixam HEIC e DICOM sem tipo (ou como octet-stream): a extensão decide; a API confere pelos bytes. */
export function contentTypeFor(file: { name: string; type: string }): string {
  const ext = file.name.toLowerCase().split(".").pop() ?? "";
  if (ext === "dcm" || ext === "dicom") return "application/dicom";
  if (ext === "heic" || ext === "heif") return "image/heic";
  if (file.type && file.type !== "application/octet-stream") return file.type;
  if (ext === "jpg" || ext === "jpeg") return "image/jpeg";
  if (ext === "png") return "image/png";
  return file.type || "application/octet-stream";
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/** Relatório em frases curtas para o autor conferir antes de aprovar (só nomes de campos e contagens). */
export function describeReport(r: MediaReport | null): string[] {
  if (!r) return [];
  const out: string[] = [];
  if (r.detectedFormat === "DICOM") {
    out.push(`DICOM desidentificado: ${plural(r.dicomAttributesRemoved.length, "campo alterado", "campos alterados")}${r.dicomAttributesRemoved.length ? ` (${r.dicomAttributesRemoved.slice(0, 6).join(", ")}${r.dicomAttributesRemoved.length > 6 ? "…" : ""})` : ""}.`);
    if (r.dicomUidsRemapped > 0) out.push(`${plural(r.dicomUidsRemapped, "identificador UID trocado", "identificadores UID trocados")}.`);
    if (r.dicomBurnedInAnnotation) out.push("O DICOM declara anotação gravada na imagem: confira os cantos com atenção.");
  } else {
    out.push(r.removedMetadata.length ? `Metadados removidos: ${r.removedMetadata.join(", ")}.` : "Nenhum metadado encontrado no arquivo.");
    if (r.gpsRemoved) out.push("Localização (GPS) removida.");
    if (r.exifOrientationApplied > 1) out.push("Rotação da câmera aplicada aos pixels.");
  }
  out.push(r.textRegionsRedacted ? `${plural(r.textRegionsRedacted, "trecho de texto coberto", "trechos de texto cobertos")} com tarja preta.` : "Nenhum texto encontrado na imagem.");
  out.push(r.faceDetection === "executada" ? (r.facesBlurred ? `${plural(r.facesBlurred, "rosto borrado", "rostos borrados")}.` : "Nenhum rosto encontrado.") : "Rostos não foram borrados (imagem dermatológica).");
  if (r.manualRedactions) out.push(`${plural(r.manualRedactions, "tarja manual sua", "tarjas manuais suas")}.`);
  return [...out, ...r.notes];
}
