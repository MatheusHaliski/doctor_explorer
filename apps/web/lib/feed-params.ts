/** Os quatro eixos de segmentação do feed (RF04) + "só sem resposta", em URL ↔ estado. */
export type FeedParams = {
  kind: string; // "" = todos
  specialty: string[]; // [] = todas
  origin: string; // "" | grupo | rede | instituicao
  state: string; // "" | SEM_RESPOSTA | EM_DISCUSSAO | RESOLVIDO
  unanswered: boolean;
};

export const EMPTY_FEED: FeedParams = { kind: "", specialty: [], origin: "", state: "", unanswered: false };

export function parseFeedParams(sp: URLSearchParams | Record<string, string | string[] | undefined>): FeedParams {
  const get = (k: string): string => {
    if (sp instanceof URLSearchParams) return sp.get(k) ?? "";
    const v = sp[k];
    return Array.isArray(v) ? v[0] ?? "" : v ?? "";
  };
  return {
    kind: get("kind").toUpperCase(),
    specialty: get("specialty").split(",").map((s) => s.trim().toUpperCase()).filter(Boolean),
    origin: get("origin").toLowerCase(),
    state: get("state").toUpperCase(),
    unanswered: get("unanswered") === "true",
  };
}

export function feedQuery(p: FeedParams): Record<string, string | boolean> {
  return {
    kind: p.kind,
    specialty: p.specialty.join(","),
    origin: p.origin,
    state: p.state,
    unanswered: p.unanswered,
  };
}

export function feedSearch(p: FeedParams): string {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(feedQuery(p))) {
    if (v === "" || v === false) continue;
    qs.set(k, String(v));
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
}

export function toggleSpecialty(p: FeedParams, code: string): FeedParams {
  const has = p.specialty.includes(code);
  return { ...p, specialty: has ? p.specialty.filter((c) => c !== code) : [...p.specialty, code] };
}
