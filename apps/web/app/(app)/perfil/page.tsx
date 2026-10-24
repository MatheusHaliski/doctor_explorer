"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { useAsyncEffect } from "@/lib/use-async";
import { SPECIALTIES, UFS, VISIBILITIES, formatDateTime } from "@/lib/domain";
import { Seal } from "@/components/Seal";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Me = { id: string; displayName: string; email: string; status: string };
type Profile = { id: string; displayName: string; seal: string; crm: string; specialties: string[]; bio?: string; visibility?: string };
type Claim = { specialtyCode: string; rqe: string; confirmed: boolean };
type Credentials = { seal: string; crmNumber: string; crmUf: string; specialties: Claim[]; checks: Array<{ source: string; checkedAt: string; outcome: string; reason: string }> };

export default function ProfilePage() {
  return (
    <Suspense fallback={<Loading />}>
      <ProfileEditor />
    </Suspense>
  );
}

/** RF03 · meu perfil (nome, apresentação, visibilidade) + RF02 · envio de CRM/UF e RQE e evidências do selo. */
function ProfileEditor() {
  const novo = useSearchParams().get("novo") === "1";
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [cred, setCred] = useState<Credentials | null>(null);
  const [edit, setEdit] = useState({ displayName: "", bio: "", visibility: "VERIFIED_NETWORK" });
  const [crm, setCrm] = useState({ crmNumber: "", crmUf: "PR" });
  const [claims, setClaims] = useState<Array<{ specialtyCode: string; rqe: string }>>([]);
  const [error, setError] = useState<unknown>(null);
  const [credError, setCredError] = useState<unknown>(null);
  const [busy, setBusy] = useState<"profile" | "cred" | null>(null);
  const toast = useToast();

  async function load() {
    const m = await api.get<Me>("/api/v1/me");
    setMe(m);
    const [p, c] = await Promise.all([api.get<Profile>(`/api/v1/profiles/${m.id}`), api.get<Credentials>("/api/v1/credentials")]);
    setProfile(p);
    setCred(c);
    setEdit({ displayName: p.displayName, bio: p.bio ?? "", visibility: p.visibility ?? "VERIFIED_NETWORK" });
    if (c.crmNumber) setCrm({ crmNumber: c.crmNumber, crmUf: c.crmUf });
    if (c.specialties.length) setClaims(c.specialties.map((s) => ({ specialtyCode: s.specialtyCode, rqe: s.rqe })));
  }
  useAsyncEffect(load, setError, []);

  const errs = (e: unknown, f: string) => (e instanceof ApiError ? e.field(f) : []);

  async function saveProfile(e: FormEvent) {
    e.preventDefault();
    setBusy("profile");
    setError(null);
    try {
      const r = await api.put<{ note?: string }>("/api/v1/me/profile", edit);
      toast.show(r.note ?? "Perfil salvo.");
      await load();
      router.refresh(); // selo e nome na topbar vêm do layout do servidor
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function submitCredentials(e: FormEvent) {
    e.preventDefault();
    setBusy("cred");
    setCredError(null);
    try {
      await api.post("/api/v1/credentials", { ...crm, specialties: claims.filter((c) => c.specialtyCode) });
      toast.show("Dados enviados. O selo é conferido na fonte oficial do CFM.");
      await load();
      router.refresh();
    } catch (err) {
      setCredError(err);
    } finally {
      setBusy(null);
    }
  }

  if (!me || !profile || !cred) return error ? <ProblemAlert error={error} /> : <Loading what="Carregando o perfil" />;
  const canSubmit = ["NONE", "REJECTED", "REVOKED"].includes(cred.seal);

  return (
    <div className="max-w-3xl">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="m-0">{profile.displayName}</h1>
        <Seal status={cred.seal} />
      </div>
      <p className="lead">
        {me.email}
        {profile.crm && ` · ${profile.crm}`}
        {profile.specialties.length > 0 && ` · ${profile.specialties.map((s) => SPECIALTIES[s] ?? s).join(", ")}`}
      </p>
      {novo && <Notice tone="success">E-mail confirmado. Falta o selo: envie CRM, UF e RQE abaixo para publicar, conversar e aparecer no diretório.</Notice>}

      <h2>Selo profissional</h2>
      <form onSubmit={submitCredentials} noValidate className="card">
        {cred.seal === "VERIFIED" && <Notice tone="success">CRM verificado na fonte oficial. Mudar nome, CRM ou especialidade devolve o selo para análise.</Notice>}
        {cred.seal === "PENDING" && <Notice tone="warning">Em análise: a fonte do CFM ainda não respondeu. Tentamos de novo por até 24 h; depois um moderador revisa.</Notice>}
        {cred.seal === "MANUAL_REVIEW" && <Notice tone="warning">Em revisão manual por um moderador. Você recebe um e-mail quando terminar.</Notice>}
        {(cred.seal === "REJECTED" || cred.seal === "REVOKED") && cred.checks[0] && <Notice tone="danger">{cred.checks[0].reason || "A verificação não conferiu. Corrija os dados e reenvie."}</Notice>}
        <div className="grid" style={{ gridTemplateColumns: "2fr 1fr" }}>
          <Field id="crmNumber" label="Número do CRM" errors={errs(credError, "crmNumber")}>
            {(p) => <input {...p} type="text" inputMode="numeric" value={crm.crmNumber} disabled={!canSubmit} onChange={(e) => setCrm({ ...crm, crmNumber: e.target.value })} />}
          </Field>
          <Field id="crmUf" label="UF" errors={errs(credError, "crmUf")}>
            {(p) => (
              <select {...p} value={crm.crmUf} disabled={!canSubmit} onChange={(e) => setCrm({ ...crm, crmUf: e.target.value })}>
                {UFS.map((uf) => (
                  <option key={uf}>{uf}</option>
                ))}
              </select>
            )}
          </Field>
        </div>
        <p className="lbl font-bold text-sm">Especialidades e RQE</p>
        {claims.map((c, i) => (
          <div key={i} className="grid" style={{ gridTemplateColumns: "2fr 1fr auto" }}>
            <Field id={`spec-${i}`} label="Especialidade" errors={errs(credError, `specialties[${i}].specialtyCode`)}>
              {(p) => (
                <select {...p} value={c.specialtyCode} disabled={!canSubmit} onChange={(e) => setClaims(claims.map((x, j) => (j === i ? { ...x, specialtyCode: e.target.value } : x)))}>
                  <option value="">Escolha</option>
                  {Object.entries(SPECIALTIES).map(([code, name]) => (
                    <option key={code} value={code}>
                      {name}
                    </option>
                  ))}
                </select>
              )}
            </Field>
            <Field id={`rqe-${i}`} label="RQE" errors={errs(credError, `specialties[${i}].rqe`)}>
              {(p) => <input {...p} type="text" inputMode="numeric" value={c.rqe} disabled={!canSubmit} onChange={(e) => setClaims(claims.map((x, j) => (j === i ? { ...x, rqe: e.target.value } : x)))} />}
            </Field>
            <div className="field">
              <span className="lbl">&nbsp;</span>
              <Button type="button" size="sm" disabled={!canSubmit} onClick={() => setClaims(claims.filter((_, j) => j !== i))} aria-label={`Remover especialidade ${i + 1}`}>
                Remover
              </Button>
            </div>
          </div>
        ))}
        {cred.specialties.length > 0 && !canSubmit && (
          <ul className="list">
            {cred.specialties.map((s) => (
              <li key={s.specialtyCode} className="item">
                <span className="text">
                  {SPECIALTIES[s.specialtyCode] ?? s.specialtyCode} · RQE {s.rqe}
                </span>
                <span className="base">{s.confirmed ? "confirmado" : "não confirmado"}</span>
              </li>
            ))}
          </ul>
        )}
        <ProblemAlert error={credError} />
        <div className="toolbar">
          {canSubmit && (
            <>
              <Button type="button" size="sm" onClick={() => setClaims([...claims, { specialtyCode: "", rqe: "" }])}>
                Adicionar especialidade
              </Button>
              <Button type="submit" variant="primary" loading={busy === "cred"}>
                {cred.seal === "NONE" ? "Enviar para verificação" : "Reenviar para verificação"}
              </Button>
            </>
          )}
        </div>
        {cred.checks.length > 0 && (
          <details className="mt-3">
            <summary className="cursor-pointer text-sm font-bold">Evidências das consultas ({cred.checks.length})</summary>
            <ul className="list">
              {cred.checks.map((c, i) => (
                <li key={i} className="item">
                  <span className="text">
                    {c.outcome} <small>{c.reason || c.source}</small>
                  </span>
                  <span className="base">{formatDateTime(c.checkedAt)}</span>
                </li>
              ))}
            </ul>
          </details>
        )}
      </form>

      <h2>Perfil público</h2>
      <form onSubmit={saveProfile} noValidate className="card">
        <Field id="displayName" label="Nome" errors={errs(error, "displayName")} help="Mudar o nome devolve o selo para análise.">
          {(p) => <input {...p} type="text" value={edit.displayName} onChange={(e) => setEdit({ ...edit, displayName: e.target.value })} />}
        </Field>
        <Field id="bio" label="Apresentação" errors={errs(error, "bio")} help="Até 1000 caracteres. Sem promessa de resultado, preço ou “o melhor” (Res. CFM 2.336/2023).">
          {(p) => <textarea {...p} value={edit.bio} maxLength={1000} onChange={(e) => setEdit({ ...edit, bio: e.target.value })} />}
        </Field>
        <Field id="visibility" label="Quem vê o perfil completo" errors={errs(error, "visibility")} help={VISIBILITIES[edit.visibility]?.help}>
          {(p) => (
            <select {...p} value={edit.visibility} onChange={(e) => setEdit({ ...edit, visibility: e.target.value })}>
              {Object.entries(VISIBILITIES).map(([code, v]) => (
                <option key={code} value={code}>
                  {v.label}
                </option>
              ))}
            </select>
          )}
        </Field>
        <ProblemAlert error={error} />
        <div className="toolbar">
          <Button type="submit" variant="primary" loading={busy === "profile"}>
            Salvar perfil
          </Button>
        </div>
      </form>
      {toast.el}
    </div>
  );
}
