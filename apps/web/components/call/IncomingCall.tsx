"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";

type Incoming = { id: string; conversationKind: string; conversationTitle: string | null; startedByName: string; state: string };

/** RF07 · aviso de chamada recebida, sem conteúdo: quem chama e de qual conversa. Atender leva à tela da chamada. */
export function IncomingCall() {
  const router = useRouter();
  const pathname = usePathname();
  const [items, setItems] = useState<Incoming[]>([]);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  useEffect(() => {
    let alive = true;
    const load = () =>
      api
        .get<{ items: Incoming[] }>("/api/v1/calls/incoming")
        .then((r) => alive && setItems(r.items))
        .catch(() => {});
    void load();
    const t = setInterval(load, 5000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, []);

  const call = items.find((c) => !dismissed.has(c.id) && !pathname.startsWith(`/chamadas/${c.id}`));
  if (!call) return null;
  const where = call.conversationKind === "GROUP" ? ` no grupo ${call.conversationTitle ?? ""}` : "";
  return (
    <div className="incoming-call" role="alertdialog" aria-labelledby="incoming-title">
      <strong id="incoming-title">
        {call.state === "RINGING" ? "Chamada de vídeo" : "Chamada em andamento"}: {call.startedByName}
        {where}
      </strong>
      <div className="toolbar">
        <button type="button" className="btn primary sm" onClick={() => router.push(`/chamadas/${call.id}`)}>
          Atender
        </button>
        <button
          type="button"
          className="btn sm"
          onClick={() => {
            setDismissed(new Set([...dismissed, call.id]));
            if (call.state === "RINGING") void api.post(`/api/v1/calls/${call.id}/decline`).catch(() => {});
          }}
        >
          Recusar
        </button>
      </div>
    </div>
  );
}
