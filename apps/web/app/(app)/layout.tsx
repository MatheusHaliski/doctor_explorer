import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { AppearanceSync } from "@/components/AppearanceSync";
import { SessionProvider } from "@/components/session-context";
import { DEFAULT_APPEARANCE, type Appearance } from "@/lib/appearance";
import { Shell } from "@/components/Shell";
import { getSession, serverGet } from "@/lib/server-api";

/** Área autenticada: a sessão é conferida no servidor a cada navegação; sem sessão, volta ao login. */
export default async function AppLayout({ children }: { children: ReactNode }) {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.status === "PENDING_VERIFICATION") redirect("/verificar-email");
  const [cred, prefs] = await Promise.all([serverGet<{ seal: string }>("/api/v1/credentials"), serverGet<Appearance>("/api/v1/me/preferences")]);
  const appearance: Appearance = prefs.data
    ? { theme: prefs.data.theme, fontScale: prefs.data.fontScale, background: prefs.data.background, backgroundVeil: prefs.data.backgroundVeil }
    : DEFAULT_APPEARANCE;
  return (
    <SessionProvider value={{ ...session, roles: session.roles ?? [] }}>
      {prefs.data && <AppearanceSync value={appearance} />}
      <Shell user={session} seal={cred.data?.seal ?? "NONE"}>
        {children}
      </Shell>
    </SessionProvider>
  );
}
