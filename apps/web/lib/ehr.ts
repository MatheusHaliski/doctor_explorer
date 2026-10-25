/** RF08 · tipos e utilidades do prontuário no web (sem estado; testadas em tests/ehr.test.ts). */

export type PatientView = { id: string; name: string; birthDate: string; cpf: string | null; sex: string | null; localRecordNumber: string | null; lastRecordAt: string };
export type Link = { type: "POST" | "CONVERSATION" | "CALL"; ref: string };
export type Code = { code: string; display: string };
export type Composition = {
  id: string;
  lineageId: string;
  version: number;
  state: "RASCUNHO" | "ASSINADA" | "RETIFICADA" | "DESCARTADA";
  patientId: string;
  patientName: string;
  encounter: { id: string; context: string; occurredAt: string };
  author: { id: string; name: string; crm: string | null; uf: string | null };
  subjective: string;
  objective: string;
  assessment: { text: string; icd10: Code[]; snomed: string[] };
  plan: string;
  links: Link[];
  amendReason: string | null;
  previousId: string | null;
  supersededBy: string | null;
  signedAt: string | null;
  contentHash: string | null;
  signature: string | null;
  signingKeyId: string | null;
  integrity: { valid: boolean; algorithm: string } | null;
  createdAt: string;
  updatedAt: string;
  editable: boolean;
  amendable: boolean;
};
export type CompositionSummary = { id: string; lineageId: string; version: number; state: Composition["state"]; context: string; occurredAt: string; icd10: string[]; signedAt: string | null; updatedAt: string; supersededBy: string | null };

export const CONTEXTS: Record<string, string> = {
  CONSULTA: "Consulta",
  DISCUSSAO_DE_CASO: "Discussão de caso",
  TELECONSULTA: "Teleconsulta",
  INTERCONSULTA: "Interconsulta",
};

export const NOTE_STATES: Record<Composition["state"], string> = {
  RASCUNHO: "Rascunho",
  ASSINADA: "Assinada",
  RETIFICADA: "Retificada (há versão mais nova)",
  DESCARTADA: "Descartada",
};

/** Idade em anos completos na data `now`. */
export function ageFrom(birthDate: string, now = new Date()): number {
  const [y, m, d] = birthDate.split("-").map(Number);
  let age = now.getFullYear() - y!;
  if (now.getMonth() + 1 < m! || (now.getMonth() + 1 === m && now.getDate() < d!)) age--;
  return age;
}

/** O que o editor manda no PUT de rascunho. */
export type DraftInput = { subjective: string; objective: string; assessment: { text: string; icd10: string[]; snomed: string[] }; plan: string };

export function toDraft(c: Composition): DraftInput {
  return {
    subjective: c.subjective,
    objective: c.objective,
    assessment: { text: c.assessment.text, icd10: c.assessment.icd10.map((x) => x.code), snomed: c.assessment.snomed },
    plan: c.plan,
  };
}

export function sameDraft(a: DraftInput, b: DraftInput): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/** Versões agrupadas por nota (linhagem), a mais recente primeiro. */
export function groupByLineage(items: CompositionSummary[]): CompositionSummary[][] {
  const map = new Map<string, CompositionSummary[]>();
  for (const it of items) map.set(it.lineageId, [...(map.get(it.lineageId) ?? []), it]);
  return [...map.values()]
    .map((v) => v.sort((a, b) => b.version - a.version))
    .sort((a, b) => (b[0]!.updatedAt > a[0]!.updatedAt ? 1 : -1));
}

export function isSudoProblem(err: unknown): boolean {
  return typeof err === "object" && err !== null && "problem" in err && String((err as { problem: { type?: string } }).problem.type ?? "").endsWith("sudo-necessario");
}
