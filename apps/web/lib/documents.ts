/** RF09 · tipos e utilidades de receita e pedido de exame no web (sem estado; testadas em tests/documents.test.ts). */

export type Kind = "RECEITA" | "EXAME";
export type DocState = "RASCUNHO" | "ASSINANDO" | "ASSINADA" | "ENVIADA" | "CANCELADA";

export type MedicationInput = {
  ggrem: string;
  dose: string;
  doseUnit: string;
  route: string;
  intervalHours: number | null;
  timesPerDay: number | null;
  durationDays: number | null;
  continuous: boolean;
  quantity: string | null;
  instructions: string | null;
};
export type ExamInput = { loinc: string };
export type Content = { medications: MedicationInput[]; exams: ExamInput[]; indication: string; notes: string };

export type MedicationView = { input: MedicationInput; label: string; substance: string; presentation: string; antimicrobial: boolean; posology: string };
export type ExamView = { loinc: string; name: string; tuss: string; tussTerm: string };
export type DeliveryView = { channel: Channel; destination: string | null; expiresAt: string; usedAt: string | null; createdAt: string };
export type Channel = "EMAIL" | "SMS" | "LINK";

export type DocumentView = {
  id: string;
  kind: Kind;
  state: DocState;
  patientId: string;
  patientName: string;
  encounterId: string;
  medications: MedicationView[];
  exams: ExamView[];
  indication: string;
  notes: string;
  contentHash: string;
  validationCode: string | null;
  validationUrl: string | null;
  signedAt: string | null;
  validUntil: string | null;
  signatureLevel: string | null;
  signatureProvider: string | null;
  certSubject: string | null;
  pdfSha256: string | null;
  failureReason: string | null;
  cancelReason: string | null;
  canceledAt: string | null;
  deliveries: DeliveryView[];
  createdAt: string;
  updatedAt: string;
};
export type DocumentSummary = { id: string; kind: Kind; state: DocState; items: number; signedAt: string | null; validUntil: string | null; createdAt: string };
export type Template = { id: string; kind: Kind; name: string; content: Content };

export type CatalogMedication = {
  ggrem: string;
  substance: string;
  product: string;
  presentation: string;
  laboratory: string;
  tarja: string;
  specialControl: boolean;
  antimicrobial: boolean;
};
export type CatalogExam = { loinc: string; name: string; tuss: string; tussTerm: string };

export type PublicView = {
  kind: Kind;
  status: "VALIDA" | "EXPIRADA" | "CANCELADA" | "ASSINATURA_INVALIDA";
  statusText: string;
  issuedAt: string;
  validUntil: string | null;
  doctorName: string;
  crm: string;
  uf: string;
  patientInitials: string;
  items: string[];
  signatureValid: boolean;
  signatureLevel: string;
  simulated: boolean;
  pdfSha256: string;
  canceledAt: string | null;
};

export const KINDS: Record<Kind, string> = { RECEITA: "Receita", EXAME: "Pedido de exame" };

export const DOC_STATES: Record<DocState, string> = {
  RASCUNHO: "Rascunho",
  ASSINANDO: "Assinando…",
  ASSINADA: "Assinada",
  ENVIADA: "Enviada ao paciente",
  CANCELADA: "Cancelada",
};

export const ROUTES: Record<string, string> = {
  ORAL: "Oral",
  SUBLINGUAL: "Sublingual",
  TOPICA: "Tópica",
  OFTALMICA: "Oftálmica",
  OTOLOGICA: "Otológica",
  NASAL: "Nasal",
  INALATORIA: "Inalatória",
  RETAL: "Retal",
  VAGINAL: "Vaginal",
  INTRAMUSCULAR: "Intramuscular",
  INTRAVENOSA: "Intravenosa",
  SUBCUTANEA: "Subcutânea",
  TRANSDERMICA: "Transdérmica",
};

export const UNITS = ["comprimido", "cápsula", "drágea", "mL", "gota", "sachê", "aplicação", "jato", "ampola", "unidade", "g"] as const;

export const CHANNELS: Record<Channel, string> = { EMAIL: "E-mail", SMS: "SMS", LINK: "Só gerar o link" };

/** Passos do fluxo: montar os itens, revisar o resumo, assinar, entregar. */
export const STEPS = ["Itens", "Revisão", "Assinatura", "Entrega"] as const;
export type Step = (typeof STEPS)[number];

export function stepOf(state: DocState, reviewing: boolean): Step {
  if (state === "RASCUNHO") return reviewing ? "Revisão" : "Itens";
  if (state === "ASSINANDO") return "Assinatura";
  return "Entrega";
}

/** Unidade e via prováveis pela apresentação da CMED; o médico confere e ajusta. */
export function guessUnitAndRoute(presentation: string): { doseUnit: string; route: string } {
  const p = presentation.toUpperCase();
  if (/\bCAP\b|CÁPS|CAPS/.test(p)) return { doseUnit: "cápsula", route: "ORAL" };
  if (/\bDRG\b|DRÁG/.test(p)) return { doseUnit: "drágea", route: "ORAL" };
  if (/\bCOM\b|COMP|\bCPR\b/.test(p)) return { doseUnit: "comprimido", route: "ORAL" };
  if (/COLÍRIO|COL OFT|OFT\b/.test(p)) return { doseUnit: "gota", route: "OFTALMICA" };
  if (/SOL GOT|GOTAS|\bGTS\b/.test(p)) return { doseUnit: "gota", route: "ORAL" };
  if (/SPR NAS|NASAL/.test(p)) return { doseUnit: "jato", route: "NASAL" };
  if (/AER|INAL/.test(p)) return { doseUnit: "jato", route: "INALATORIA" };
  if (/CREM|POM|GEL\b|LOC\b/.test(p)) return { doseUnit: "aplicação", route: "TOPICA" };
  if (/SUS OR|XPE|SOL OR|SUSP/.test(p)) return { doseUnit: "mL", route: "ORAL" };
  if (/SOL INJ|INJ\b|AMP/.test(p)) return { doseUnit: "ampola", route: "INTRAMUSCULAR" };
  if (/SACH|PÓ/.test(p)) return { doseUnit: "sachê", route: "ORAL" };
  return { doseUnit: "unidade", route: "ORAL" };
}

/** Item novo a partir do catálogo, com uma posologia de partida que o médico revisa. */
export function newMedication(m: CatalogMedication): MedicationInput {
  return {
    ggrem: m.ggrem,
    dose: "1",
    ...guessUnitAndRoute(m.presentation),
    intervalHours: 8,
    timesPerDay: null,
    durationDays: m.antimicrobial ? 7 : 5,
    continuous: false,
    quantity: null,
    instructions: null,
  };
}

/** O que o editor manda no PUT a partir da visão do servidor. */
export function toContent(d: DocumentView): Content {
  return { medications: d.medications.map((m) => ({ ...m.input, continuous: Boolean(m.input.continuous) })), exams: d.exams.map((e) => ({ loinc: e.loinc })), indication: d.indication, notes: d.notes };
}

export function sameContent(a: Content, b: Content): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/** Código de validação no formato impresso (XXXX-XXXX-XXXX-XXXX), aceitando minúsculas, espaços e O/I/L do Crockford. */
export function normalizeCode(raw: string): string | null {
  const c = raw
    .toUpperCase()
    .replace(/[\s-]/g, "")
    .replace(/O/g, "0")
    .replace(/[IL]/g, "1");
  if (!/^[0-9A-HJKMNP-TV-Z]{16}$/.test(c)) return null;
  return c.match(/.{4}/g)!.join("-");
}

export function isSigned(state: DocState): boolean {
  return state === "ASSINADA" || state === "ENVIADA";
}

export function tarjaLabel(m: CatalogMedication): string {
  if (m.specialControl) return "Controle especial (fora do MVP)";
  if (m.antimicrobial) return "Antimicrobiano · retenção da 2ª via";
  if (m.tarja === "VERMELHA" || m.tarja === "VERMELHA_RESTRICAO") return "Tarja vermelha";
  if (m.tarja === "SEM_TARJA") return "Sem tarja";
  return "";
}

/** Primeira mensagem de erro de um item da lista (ex.: medications[1].dose). */
export function itemErrors(errors: { field: string; message: string }[], prefix: string): string[] {
  return errors.filter((e) => e.field === prefix || e.field.startsWith(prefix + ".")).map((e) => e.message);
}
