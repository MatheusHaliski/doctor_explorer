import { cookies } from "next/headers";

/** Chamadas feitas no servidor (Server Components) repassando o cookie de sessão do navegador para a core-api. */
const API_ORIGIN = process.env.API_ORIGIN ?? "http://localhost:8080";

export type Session = {
  userId: string;
  displayName: string;
  status: "PENDING_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "PENDING_DELETION" | "ANONYMOUS_LGPD_DELETED";
  roles: string[]; // papéis globais sem o prefixo ROLE_ (PROFESSIONAL, MODERATOR, ADMIN, DPO…)
};

export async function serverGet<T>(path: string): Promise<{ status: number; data: T | null }> {
  const jar = await cookies();
  const cookie = jar
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  try {
    const res = await fetch(`${API_ORIGIN}${path}`, { headers: { Accept: "application/json", cookie }, cache: "no-store" });
    if (!res.ok) return { status: res.status, data: null };
    return { status: res.status, data: (await res.json()) as T };
  } catch {
    return { status: 0, data: null };
  }
}

/** Sessão atual ou `null` (sem cookie, expirada ou API fora). */
export async function getSession(): Promise<Session | null> {
  const { data } = await serverGet<Session>("/api/v1/auth/session");
  return data;
}
