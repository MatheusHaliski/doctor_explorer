"use client";

import { createContext, useContext, type ReactNode } from "react";

export type SessionInfo = { userId: string; displayName: string; status: string; roles: string[] };

const Ctx = createContext<SessionInfo | null>(null);

/** Sessão (vinda do servidor no layout) disponível para qualquer componente cliente da área logada. */
export function SessionProvider({ value, children }: { value: SessionInfo; children: ReactNode }) {
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession(): SessionInfo {
  const s = useContext(Ctx);
  if (!s) throw new Error("useSession fora da área logada");
  return s;
}

export function isModerator(s: SessionInfo | null | undefined): boolean {
  return !!s && s.roles.some((r) => r === "MODERATOR" || r === "ADMIN");
}
