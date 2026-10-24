"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { useAsyncEffect } from "@/lib/use-async";
import { formatDateTime } from "@/lib/domain";
import { Button, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Institution = {
  id: string; name: string; type: string; city: string; uf: string; cnes: string; verified: boolean; parentId: string;
  units: Array<{ id: string; name: string; type: string }>; clinicalStaffCount: number; myRole?: string;
};
type Staff = { userId: string; name: string; role: string; since: string };
const ROLES: Record<string, string> = { NETWORK_ADMIN: "Admin da rede", HOSPITAL_ADMIN: "Gestor do hospital", CLINICAL_DIRECTOR: "Diretor clínico", UNIT_MANAGER: "Gestor da unidade", AUDITOR: "Auditor", STAFF_PHYSICIAN: "Corpo clínico", NONE: "Sem vínculo" };
const MANAGER = new Set(["NETWORK_ADMIN", "HOSPITAL_ADMIN", "CLINICAL_DIRECTOR", "UNIT_MANAGER"]);

/** RF11 · instituição: unidades, corpo clínico verificado e pedido de vínculo. */
export function InstitutionView({ id }: { id: string }) {
  const [i, setI] = useState<Institution | null>(null);
  const [staff, setStaff] = useState<Staff[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = useCallback(async () => {
    const inst = await api.get<Institution>(`/api/v1/institutions/${id}`);
    setI(inst);
    api.get<{ staff: Staff[] }>(`/api/v1/institutions/${id}/staff`).then((s) => setStaff(s.staff)).catch(() => setStaff([]));
  }, [id]);
  useAsyncEffect(load, setError, [load]);

  async function join() {
    setBusy(true);
    setError(null);
    try {
      const r = await api.post<{ state: string }>(`/api/v1/institutions/${id}/join`);
      toast.show(r.state === "ACTIVE" ? "Vínculo ativo." : "Pedido enviado ao gestor da instituição.");
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (!i) return error ? <ProblemAlert error={error} /> : <Loading />;
  return (
    <div className="max-w-3xl">
      <p className="result-line">
        <Link href="/instituicoes">← Diretório</Link>
      </p>
      <article className="card">
        <h1 className="m-0">{i.name}</h1>
        <p className="text-text-2">
          {i.city}/{i.uf}
          {i.cnes && ` · CNES ${i.cnes}`} · {i.clinicalStaffCount} no corpo clínico verificado
        </p>
        {i.verified ? <Notice tone="success">CNPJ ativo e CNES conferidos nas fontes oficiais.</Notice> : <Notice tone="warning">Cadastro em análise.</Notice>}
        <div className="toolbar">
          <span className="text-sm text-text-3">Seu vínculo: {ROLES[i.myRole ?? "NONE"] ?? i.myRole}</span>
          {(i.myRole ?? "NONE") === "NONE" && (
            <Button type="button" variant="primary" loading={busy} onClick={join}>
              Pedir vínculo / aceitar convite
            </Button>
          )}
          {MANAGER.has(i.myRole ?? "") && (
            <>
              <Link className="btn sm" href={`/instituicoes/${id}/gestao`}>
                Gestão de vínculos
              </Link>
              <Link className="btn sm" href={`/instituicoes/nova?parentId=${id}`}>
                Nova unidade
              </Link>
            </>
          )}
        </div>
        <ProblemAlert error={error} />
      </article>
      {i.units.length > 0 && (
        <>
          <h2>Unidades</h2>
          <div className="grid">
            {i.units.map((u) => (
              <Link key={u.id} href={`/instituicoes/${u.id}`} className="card no-underline" style={{ color: "inherit" }}>
                <strong>{u.name}</strong>
                <span className="block text-sm text-text-3">{u.type}</span>
              </Link>
            ))}
          </div>
        </>
      )}
      <h2>Corpo clínico verificado</h2>
      <div className="card list">
        {staff === null ? (
          <Loading />
        ) : staff.length === 0 ? (
          <p className="m-0 text-sm text-text-3">Ninguém com vínculo ativo e selo ainda.</p>
        ) : (
          staff.map((s) => (
            <div key={s.userId} className="item">
              <span className="text">
                <Link href={`/perfil/${s.userId}`}>{s.name}</Link>
                <small>desde {formatDateTime(s.since)}</small>
              </span>
              <span className="base">{ROLES[s.role] ?? s.role}</span>
            </div>
          ))
        )}
      </div>
      {toast.el}
    </div>
  );
}
