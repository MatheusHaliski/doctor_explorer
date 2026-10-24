"use client";

import Link from "next/link";
import { useCallback, useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { SPECIALTIES, formatDateTime } from "@/lib/domain";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Institution = { id: string; name: string; type: string; city: string; uf: string; myRole?: string };
type Member = { userId: string; name: string; role: string; state: "INVITED" | "REQUESTED" | "ACTIVE" | "ENDED"; since: string };
export const ROLES: Record<string, string> = { NETWORK_ADMIN: "Admin da rede", HOSPITAL_ADMIN: "Gestor do hospital", CLINICAL_DIRECTOR: "Diretor clínico", UNIT_MANAGER: "Gestor da unidade", AUDITOR: "Auditor", STAFF_PHYSICIAN: "Corpo clínico" };
const STATES: Record<Member["state"], string> = { INVITED: "convidado, aguarda aceite", REQUESTED: "pediu vínculo", ACTIVE: "ativo", ENDED: "encerrado" };
const RANK: Record<string, number> = { NETWORK_ADMIN: 6, HOSPITAL_ADMIN: 5, CLINICAL_DIRECTOR: 4, UNIT_MANAGER: 3, AUDITOR: 2, STAFF_PHYSICIAN: 1 };

/** RF11/RF12 · gestão: vínculos (aprovar, papel, encerrar) e publicações institucionais. Papel nunca acima do próprio. */
export function ManageView({ id }: { id: string }) {
  const [inst, setInst] = useState<Institution | null>(null);
  const [members, setMembers] = useState<Member[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [post, setPost] = useState({ kind: "PROTOCOL", specialtyCode: "", title: "", body: "", tags: "" });
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();

  const load = useCallback(async () => {
    const i = await api.get<Institution>(`/api/v1/institutions/${id}`);
    setInst(i);
    const m = await api.get<{ members: Member[] }>(`/api/v1/institutions/${id}/members`);
    setMembers(m.members.filter((x) => x.state !== "ENDED"));
  }, [id]);
  useAsyncEffect(load, setError, [load]);

  async function run(key: string, fn: () => Promise<string>) {
    setBusy(key);
    setError(null);
    try {
      toast.show(await fn());
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  function publish(e: FormEvent) {
    e.preventDefault();
    run("post", async () => {
      await api.post(`/api/v1/institutions/${id}/posts`, { ...post, specialtyCode: post.specialtyCode || null, tags: post.tags.split(/[,\s]+/).map((t) => t.replace(/^#/, "")).filter(Boolean) });
      setPost({ kind: "PROTOCOL", specialtyCode: "", title: "", body: "", tags: "" });
      return "Publicado no feed de quem tem vínculo ativo.";
    });
  }

  if (error instanceof ApiError && (error.status === 403 || error.status === 404) && !members) {
    return (
      <>
        <h1>Gestão</h1>
        <Notice tone="warning">Você não tem papel de gestão nesta instituição. <Link href={`/instituicoes/${id}`}>Voltar à instituição</Link>.</Notice>
      </>
    );
  }
  if (!inst || !members) return error ? <ProblemAlert error={error} /> : <Loading />;
  const myRank = RANK[inst.myRole ?? ""] ?? 0;
  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);

  return (
    <div className="max-w-3xl">
      <p className="result-line">
        <Link href={`/instituicoes/${id}`}>← {inst.name}</Link>
      </p>
      <h1>Gestão · {inst.name}</h1>
      <p className="lead">Seu papel aqui: {ROLES[inst.myRole ?? ""] ?? inst.myRole}. Você só concede papéis até o seu; o papel vale para as unidades abaixo, nunca para cima.</p>
      <ProblemAlert error={error} />

      <h2>Vínculos ({members.length})</h2>
      <div className="card list">
        {members.length === 0 && <p className="m-0 text-sm text-text-3">Ninguém vinculado ainda. Convide colegas pela página do perfil deles.</p>}
        {members.map((m) => (
          <div key={m.userId} className="item" style={{ flexWrap: "wrap" }}>
            <div className="text">
              <Link href={`/perfil/${m.userId}`} className="font-bold">
                {m.name}
              </Link>
              <small>
                {STATES[m.state]} · desde {formatDateTime(m.since)}
              </small>
            </div>
            {m.state === "REQUESTED" ? (
              <Button size="sm" variant="primary" loading={busy === `ok-${m.userId}`} onClick={() => run(`ok-${m.userId}`, async () => { await api.post(`/api/v1/institutions/${id}/members/${m.userId}`); return `${m.name} agora faz parte do corpo clínico.`; })}>
                Aceitar
              </Button>
            ) : (
              <label className="text-sm">
                <span className="sr-only">Papel de {m.name}</span>
                <select className="input" style={{ width: "auto", minHeight: 36 }} value={m.role} disabled={m.state !== "ACTIVE" || busy !== null} onChange={(e) => run(`role-${m.userId}`, async () => { await api.put(`/api/v1/institutions/${id}/members/${m.userId}/role`, { role: e.target.value }); return `${m.name}: ${ROLES[e.target.value]}.`; })}>
                  {Object.entries(ROLES).filter(([code]) => RANK[code]! <= myRank || code === m.role).map(([code, label]) => (
                    <option key={code} value={code}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <Button size="sm" loading={busy === `end-${m.userId}`} onClick={() => { if (confirm(`Encerrar o vínculo de ${m.name}?`)) run(`end-${m.userId}`, async () => { await api.delete(`/api/v1/institutions/${id}/members/${m.userId}`); return `Vínculo de ${m.name} encerrado.`; }); }}>
              Encerrar
            </Button>
          </div>
        ))}
      </div>

      <h2>Publicar protocolo ou comunicado</h2>
      <form onSubmit={publish} noValidate className="card">
        <p className="m-0 text-sm text-text-2">Entra no feed de quem tem vínculo ativo, com origem “Instituição”. Sem dado de paciente.</p>
        <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
          <Field id="kind" label="Tipo" errors={errs("kind")}>
            {(p) => (
              <select {...p} value={post.kind} onChange={(e) => setPost({ ...post, kind: e.target.value })}>
                <option value="PROTOCOL">Protocolo</option>
                <option value="NOTICE">Comunicado</option>
              </select>
            )}
          </Field>
          <Field id="specialtyCode" label="Especialidade (opcional)" errors={errs("specialtyCode")}>
            {(p) => (
              <select {...p} value={post.specialtyCode} onChange={(e) => setPost({ ...post, specialtyCode: e.target.value })}>
                <option value="">Todas</option>
                {Object.entries(SPECIALTIES).map(([code, name]) => (
                  <option key={code} value={code}>
                    {name}
                  </option>
                ))}
              </select>
            )}
          </Field>
        </div>
        <Field id="title" label="Título" errors={errs("title")}>
          {(p) => <input {...p} type="text" maxLength={160} value={post.title} onChange={(e) => setPost({ ...post, title: e.target.value })} />}
        </Field>
        <Field id="body" label="Texto" errors={errs("body")}>
          {(p) => <textarea {...p} value={post.body} onChange={(e) => setPost({ ...post, body: e.target.value })} />}
        </Field>
        <Field id="tags" label="Tags">
          {(p) => <input {...p} type="text" value={post.tags} onChange={(e) => setPost({ ...post, tags: e.target.value })} />}
        </Field>
        <div className="toolbar">
          <Button type="submit" variant="primary" loading={busy === "post"} disabled={!post.title.trim()}>
            Publicar
          </Button>
        </div>
      </form>
      {toast.el}
    </div>
  );
}
