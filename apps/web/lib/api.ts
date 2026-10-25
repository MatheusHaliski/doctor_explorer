/**
 * Cliente HTTP do navegador para a core-api (RF01/RF12).
 * - Mesma origem: `/api/*` é reescrito pelo Next para a core-api; o cookie de sessão é opaco e HttpOnly.
 * - CSRF no padrão SPA do Spring Security: cookie `XSRF-TOKEN` legível → cabeçalho `X-XSRF-TOKEN` em toda escrita.
 * - Nunca guarda token em localStorage (ADR-008).
 * - Erros chegam como Problem Details (RFC 9457) em pt-BR; `422` traz `errors[]` por campo.
 */

export type FieldProblem = { field: string; message: string };

export type Problem = {
  status: number;
  type?: string;
  title: string;
  detail: string;
  errors?: FieldProblem[];
  retryAfter?: number;
  [extra: string]: unknown;
};

export class ApiError extends Error {
  readonly problem: Problem;
  constructor(problem: Problem) {
    super(problem.detail || problem.title);
    this.name = "ApiError";
    this.problem = problem;
  }
  get status() {
    return this.problem.status;
  }
  /** Mensagens de um campo do `errors[]` (422). */
  field(name: string): string[] {
    return (this.problem.errors ?? []).filter((e) => e.field === name).map((e) => e.message);
  }
}

const WRITE = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export function readCookie(name: string, source = typeof document === "undefined" ? "" : document.cookie): string | null {
  for (const part of source.split(";")) {
    const [k, ...rest] = part.trim().split("=");
    if (k === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

/** Converte uma resposta de erro em `Problem`, aceitando corpo vazio ou não JSON. */
export async function toProblem(res: Response): Promise<Problem> {
  const retry = res.headers.get("Retry-After");
  let body: Partial<Problem> = {};
  try {
    const text = await res.text();
    if (text) body = JSON.parse(text) as Partial<Problem>;
  } catch {
    body = {};
  }
  const fallback: Record<number, [string, string]> = {
    401: ["Sessão necessária", "Entre de novo para continuar."],
    403: ["Sem permissão", "Sua conta não tem acesso a este recurso."],
    404: ["Não encontrado", "O que você procura não existe ou você não tem acesso."],
    429: ["Muitas tentativas", "Aguarde um pouco antes de tentar de novo."],
  };
  const [title, detail] = fallback[res.status] ?? ["Algo deu errado", "Tente de novo em instantes."];
  return {
    ...body,
    status: res.status,
    title: body.title ?? title,
    detail: body.detail ?? detail,
    retryAfter: retry ? Number(retry) : body.retryAfter,
  };
}

async function ensureCsrf(): Promise<string> {
  const existing = readCookie("XSRF-TOKEN");
  if (existing) return existing;
  const res = await fetch("/api/v1/auth/csrf", { credentials: "same-origin", cache: "no-store" });
  if (!res.ok) throw new ApiError(await toProblem(res));
  const data = (await res.json()) as { token: string };
  return readCookie("XSRF-TOKEN") ?? data.token;
}

export type RequestOptions = { query?: Record<string, string | number | boolean | undefined | null> };

export function withQuery(path: string, query?: RequestOptions["query"]): string {
  if (!query) return path;
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === "" || v === false) continue;
    qs.set(k, String(v));
  }
  const s = qs.toString();
  return s ? `${path}?${s}` : path;
}

async function request<T>(method: string, path: string, body?: unknown, opts?: RequestOptions): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (WRITE.has(method)) headers["X-XSRF-TOKEN"] = await ensureCsrf();
  const res = await fetch(withQuery(path, opts?.query), {
    method,
    headers,
    credentials: "same-origin",
    cache: "no-store",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw new ApiError(await toProblem(res));
  if (res.status === 204 || res.headers.get("content-length") === "0") return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Arquivo binário (PDF): mesmo CSRF e os mesmos erros em Problem Details do JSON. */
async function file(method: string, path: string): Promise<Blob> {
  const headers: Record<string, string> = { Accept: "application/pdf, application/problem+json" };
  if (WRITE.has(method)) headers["X-XSRF-TOKEN"] = await ensureCsrf();
  const res = await fetch(path, { method, headers, credentials: "same-origin", cache: "no-store" });
  if (!res.ok) throw new ApiError(await toProblem(res));
  return res.blob();
}

export const api = {
  get: <T>(path: string, opts?: RequestOptions) => request<T>("GET", path, undefined, opts),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body ?? {}),
  delete: <T>(path: string) => request<T>("DELETE", path),
  file: (method: "GET" | "POST", path: string) => file(method, path),
};

/** Texto único para mostrar ao usuário a partir de qualquer erro. */
export function describeError(e: unknown): string {
  if (e instanceof ApiError) {
    const p = e.problem;
    if (p.status === 429 && p.retryAfter) return `${p.detail} Tente de novo em ${p.retryAfter} s.`;
    return p.detail || p.title;
  }
  if (e instanceof Error) return "Sem conexão com o servidor. Verifique sua rede e tente de novo.";
  return "Algo deu errado.";
}
