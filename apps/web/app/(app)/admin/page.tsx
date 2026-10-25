"use client";

import { DashboardView } from "@/components/dashboard/DashboardView";
import { useSession } from "@/components/session-context";
import { Notice } from "@/components/ui";

/** RF13 · painel da plataforma: moderador, admin e Encarregado (a API exige o papel e 2FA). */
export default function AdminPage() {
  const s = useSession();
  if (!s.roles.some((r) => ["MODERATOR", "ADMIN", "DPO"].includes(r))) {
    return (
      <>
        <h1>Painel da plataforma</h1>
        <Notice tone="warning">Só moderadores, administradores e o Encarregado veem este painel.</Notice>
      </>
    );
  }
  return <DashboardView scope={{ kind: "platform" }} />;
}
