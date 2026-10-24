/** Vocabulário compartilhado com a core-api (nomes em pt-BR para a interface). */

export const SPECIALTIES: Record<string, string> = {
  CARDIO: "Cardiologia",
  UTI: "Medicina intensiva",
  DERM: "Dermatologia",
  PED: "Pediatria",
  ORTO: "Ortopedia",
  NEURO: "Neurologia",
  CLIN: "Clínica médica",
};

export const KINDS: Record<string, string> = {
  CASE: "Caso clínico",
  QUESTION: "Dúvida",
  EDUCATION: "Educação",
  PROTOCOL: "Protocolo",
  NOTICE: "Comunicado",
};

export const ORIGINS: Record<string, string> = { grupo: "Grupo", rede: "Rede", instituicao: "Instituição" };

export const STATES: Record<string, string> = {
  SEM_RESPOSTA: "Sem resposta",
  EM_DISCUSSAO: "Em discussão",
  RESOLVIDO: "Resolvido",
};

export const SEALS: Record<string, { label: string; icon: string; cls: string }> = {
  VERIFIED: { label: "CRM verificado", icon: "/icons/topbar/selo-verificado.svg", cls: "VERIFIED" },
  PENDING: { label: "Em análise", icon: "/icons/topbar/selo-pendente.svg", cls: "PENDING" },
  MANUAL_REVIEW: { label: "Em revisão manual", icon: "/icons/topbar/selo-pendente.svg", cls: "PENDING" },
  REJECTED: { label: "Não verificado", icon: "/icons/topbar/selo-rejeitado.svg", cls: "REJECTED" },
  REVOKED: { label: "Selo suspenso", icon: "/icons/topbar/selo-rejeitado.svg", cls: "REJECTED" },
  NONE: { label: "Sem verificação", icon: "/icons/topbar/selo-pendente.svg", cls: "PENDING" },
};

export const VISIBILITIES: Record<string, { label: string; help: string }> = {
  VERIFIED_NETWORK: { label: "Rede verificada", help: "Médicos com selo veem seu perfil completo. Visitantes não veem nada." },
  MY_INSTITUTION: { label: "Minha instituição", help: "Só colegas com vínculo em comum veem o perfil completo." },
  PUBLIC_MINIMAL: { label: "Público mínimo", help: "Qualquer pessoa vê nome, CRM e especialidades; o resto só a rede verificada." },
};

export const UFS = ["AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"];

/** Versões vigentes dos Termos e da Política (iguais às da core-api; o aceite grava a versão). */
export const TERMS_VERSION = "2026-10-24";
export const PRIVACY_VERSION = "2026-10-24";

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join("");
}

export function timeAgo(iso: string, now = Date.now()): string {
  const diff = Math.max(0, now - new Date(iso).getTime());
  const m = Math.floor(diff / 60000);
  if (m < 1) return "agora";
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} h`;
  const d = Math.floor(h / 24);
  if (d < 30) return `${d} d`;
  return new Date(iso).toLocaleDateString("pt-BR");
}

export function formatDateTime(iso: string): string {
  if (!iso || iso === "null") return "—";
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}
