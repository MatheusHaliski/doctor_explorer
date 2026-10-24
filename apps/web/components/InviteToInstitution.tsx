"use client";

import { useState } from "react";
import { api } from "@/lib/api";
import { useAsyncEffect } from "@/lib/use-async";
import { useSession } from "@/components/session-context";
import { Button, ProblemAlert, useToast } from "./ui";

type Managed = { institutionId: string; role: string; name: string };
const MANAGER_ROLES = new Set(["NETWORK_ADMIN", "HOSPITAL_ADMIN", "CLINICAL_DIRECTOR", "UNIT_MANAGER"]);

/** RF11 · na página de um colega: gestores convidam para as instituições que gerem. */
export function InviteToInstitution({ userId }: { userId: string }) {
  const session = useSession();
  const [managed, setManaged] = useState<Managed[]>([]);
  const [choice, setChoice] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  useAsyncEffect(
    async () => {
      if (session.userId === userId) return;
      const me = await api.get<{ institutions?: Array<{ institutionId: string; role: string }> }>(`/api/v1/profiles/${session.userId}`);
      const mine = (me.institutions ?? []).filter((i) => MANAGER_ROLES.has(i.role));
      const named = await Promise.all(mine.map(async (i) => ({ ...i, name: (await api.get<{ name: string }>(`/api/v1/institutions/${i.institutionId}`)).name })));
      setManaged(named);
      if (named[0]) setChoice(named[0].institutionId);
    },
    setError,
    [session.userId, userId],
  );

  if (session.userId === userId || managed.length === 0) return null;

  async function invite() {
    setBusy(true);
    setError(null);
    try {
      const r = await api.post<{ state: string }>(`/api/v1/institutions/${choice}/members/${userId}`);
      toast.show(r.state === "ACTIVE" ? "Vínculo ativo: a pessoa já tinha pedido." : "Convite enviado; vale quando a pessoa aceitar.");
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-4 card">
      <p className="m-0 text-sm font-bold">Convidar para o corpo clínico</p>
      <div className="toolbar">
        <label className="sr-only" htmlFor="inst-choice">
          Instituição
        </label>
        <select id="inst-choice" className="input" style={{ width: "auto" }} value={choice} onChange={(e) => setChoice(e.target.value)}>
          {managed.map((m) => (
            <option key={m.institutionId} value={m.institutionId}>
              {m.name}
            </option>
          ))}
        </select>
        <Button variant="primary" size="sm" loading={busy} onClick={invite}>
          Convidar
        </Button>
      </div>
      <ProblemAlert error={error} />
      {toast.el}
    </div>
  );
}
