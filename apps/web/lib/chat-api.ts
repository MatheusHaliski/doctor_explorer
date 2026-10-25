/** RF06 · o Delivery Service da core-api (/api/v1/messaging) no formato que o cliente MLS espera. */
import { NotInGroupError, StaleEpochError, type ConversationInfo, type DeliveryApi, type DeviceInfo, type Envelope, type WelcomeItem } from "@doctor-explorer/crypto";
import { api, ApiError } from "@/lib/api";

const BASE = "/api/v1/messaging";

function problemType(err: unknown): string {
  return err instanceof ApiError ? String(err.problem.type ?? "") : "";
}

async function mapped<T>(p: Promise<T>): Promise<T> {
  try {
    return await p;
  } catch (err) {
    const t = problemType(err);
    if (t.endsWith("epoca-desatualizada")) throw new StaleEpochError();
    if (t.endsWith("aparelho-fora-do-grupo")) throw new NotInGroupError();
    throw err;
  }
}

export const deliveryApi: DeliveryApi = {
  registerDevice: (name, signatureKey, recoveryVerifier) => api.post<DeviceInfo>(`${BASE}/devices`, { name, signatureKey, recoveryVerifier }),
  device: (id) => api.get<DeviceInfo>(`${BASE}/devices/${id}/public`),
  myDevices: async () => (await api.get<{ items: DeviceInfo[] }>(`${BASE}/devices`)).items,
  publishKeyPackages: async (deviceId, keyPackages) => (await api.post<{ free: number }>(`${BASE}/devices/${deviceId}/key-packages`, { keyPackages })).free,
  welcomes: async (deviceId) => (await api.get<{ items: WelcomeItem[] }>(`${BASE}/devices/${deviceId}/welcomes`)).items,
  ackWelcome: (deviceId, welcomeId) => api.delete(`${BASE}/devices/${deviceId}/welcomes/${welcomeId}`),
  createConversation: (input) => api.post<ConversationInfo>(`${BASE}/conversations`, input),
  conversation: (id) => api.get<ConversationInfo>(`${BASE}/conversations/${id}`),
  claimKeyPackages: async (id, deviceIds) =>
    (await api.post<{ items: { deviceId: string; userId: string; keyPackage: string }[] }>(`${BASE}/conversations/${id}/key-packages`, { deviceIds })).items,
  commit: (id, body) => mapped(api.post<{ seq: number; epoch: number }>(`${BASE}/conversations/${id}/commits`, body)),
  send: (id, body) => mapped(api.post<{ seq: number; epoch: number }>(`${BASE}/conversations/${id}/messages`, body)),
  messages: async (id, after) => (await api.get<{ items: Envelope[] }>(`${BASE}/conversations/${id}/messages`, { query: { after, limit: 200 } })).items,
};

export type ConversationSummary = {
  id: string;
  kind: "DIRECT" | "GROUP";
  title: string | null;
  members: { userId: string; name: string; role: string; verified: boolean }[];
  lastMessageAt: string | null;
  unread: boolean;
  retentionDays: number;
};

export const RETENTIONS: Record<number, string> = { 1: "24 horas", 7: "7 dias", 90: "90 dias" };

/** Nome da conversa para quem vê: o título do grupo ou o nome do outro participante. */
export function conversationName(c: { kind: string; title: string | null; members: { userId: string; name: string }[] }, me: string): string {
  if (c.kind === "GROUP") return c.title ?? "Grupo";
  return c.members.find((m) => m.userId !== me)?.name ?? "Conversa";
}

/**
 * Heurística local (critério 8): o texto parece falar de paciente identificável? Roda só no aparelho; nada é enviado.
 * Sugere registrar no prontuário e lembra que o chat não é prontuário.
 */
export function looksLikeIdentifiedPatient(text: string): boolean {
  return [
    /\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b/, // CPF
    /\b([Pp]aciente|[Pp]cte|[Pp]ac\.)\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ][a-záéíóúâêôãõç]+\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ]/, // paciente Nome Sobrenome
    /\b(prontu[áa]rio|registro|atendimento)\s*(n[ºo°.]?\s*)?\d{4,}/i,
    /\bnasc(ido|ida|imento)?\.?\s*(em\s*)?\d{1,2}\/\d{1,2}\/\d{2,4}/i,
    /\bcart[ãa]o\s+sus\b|\bcns\s*\d/i,
  ].some((r) => r.test(text));
}
