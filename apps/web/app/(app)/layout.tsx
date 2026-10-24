import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { SessionProvider } from "@/components/session-context";
import { Shell } from "@/components/Shell";
import { getSession, serverGet } from "@/lib/server-api";

/** Área autenticada: a sessão é conferida no servidor a cada navegação; sem sessão, volta ao login. */
export default async function AppLayout({ children }: { children: ReactNode }) {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.status === "PENDING_VERIFICATION") redirect("/verificar-email");
  const cred = await serverGet<{ seal: string }>("/api/v1/credentials");
  return (
    <SessionProvider value={{ ...session, roles: session.roles ?? [] }}>
      <Shell user={session} seal={cred.data?.seal ?? "NONE"}>
        {children}
      </Shell>
    </SessionProvider>
  );
}
