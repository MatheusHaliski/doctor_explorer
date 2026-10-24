"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { SPECIALTIES } from "@/lib/domain";
import { Button, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Group = { specialtyCode: string; name: string; joinPolicy: string; membership: "NONE" | "REQUESTED" | "ACTIVE" };
const MEMBERSHIP: Record<Group["membership"], string> = { NONE: "", REQUESTED: "Pedido enviado", ACTIVE: "Você é membro" };

/** RF04 · grupos por especialidade: entra na hora com RQE confirmado; sem RQE, pede e espera o moderador. */
export default function GroupsPage() {
  const [groups, setGroups] = useState<Group[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();

  const load = () => api.get<{ groups: Group[] }>("/api/v1/groups").then((g) => setGroups(g.groups)).catch(setError);
  useEffect(() => {
    load();
  }, []);

  async function join(code: string) {
    setBusy(code);
    setError(null);
    try {
      const r = await api.post<{ state: string; note: string }>(`/api/v1/groups/${code.toLowerCase()}/join`);
      toast.show(r.note);
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  if (error instanceof ApiError && error.status === 403)
    return (
      <>
        <h1>Grupos</h1>
        <Notice tone="warning">
          Grupos são só para contas com CRM verificado. <Link href="/perfil">Envie seu CRM e RQE no perfil</Link>.
        </Notice>
      </>
    );

  return (
    <>
      <h1>Grupos</h1>
      <p className="lead">Um grupo por especialidade. Com RQE confirmado você entra na hora; sem RQE, um moderador aprova.</p>
      <ProblemAlert error={error} />
      {!groups ? (
        <Loading />
      ) : (
        <div className="grid">
          {groups.map((g) => (
            <div key={g.specialtyCode} className="card">
              <h3 className="m-0 text-lg">{SPECIALTIES[g.specialtyCode] ?? g.name}</h3>
              <p className="text-sm text-text-3">
                {g.joinPolicy === "RQE_OR_APPROVAL" ? "Entrada com RQE ou aprovação" : g.joinPolicy}
                {g.membership !== "NONE" && ` · ${MEMBERSHIP[g.membership]}`}
              </p>
              <div className="toolbar">
                {g.membership === "ACTIVE" ? (
                  <Link className="btn sm" href={`/feed?specialty=${g.specialtyCode}&origin=grupo`}>
                    Ver publicações
                  </Link>
                ) : (
                  <Button size="sm" variant="primary" loading={busy === g.specialtyCode} disabled={g.membership === "REQUESTED"} onClick={() => join(g.specialtyCode)}>
                    {g.membership === "REQUESTED" ? "Aguardando aprovação" : "Entrar"}
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
      {toast.el}
    </>
  );
}
