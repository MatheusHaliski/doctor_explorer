"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { UFS } from "@/lib/domain";
import { Button, Field, Loading, Notice, ProblemAlert } from "@/components/ui";

export default function NewInstitutionPage() {
  return (
    <Suspense fallback={<Loading />}>
      <NewInstitutionForm />
    </Suspense>
  );
}

/** RF11 · cadastro de rede, hospital ou unidade: CNPJ ativo e CNES do mesmo CNPJ conferidos nas fontes oficiais. Quem cadastra vira gestor. */
function NewInstitutionForm() {
  const router = useRouter();
  const parentId = useSearchParams().get("parentId") ?? "";
  const [form, setForm] = useState({ legalName: "", tradeName: "", cnpj: "", cnes: "", type: parentId ? "UNIT" : "HOSPITAL", city: "", uf: "PR", lat: "", lng: "" });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api.post<{ id: string; verified: boolean }>("/api/v1/institutions", {
        ...form,
        parentId: parentId || null,
        lat: form.lat ? Number(form.lat) : null,
        lng: form.lng ? Number(form.lng) : null,
      });
      router.push(`/instituicoes/${r.id}${r.verified ? "/gestao" : ""}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="max-w-3xl">
      <p className="result-line">
        <Link href="/instituicoes">← Diretório</Link>
      </p>
      <h1>{parentId ? "Nova unidade" : "Cadastrar instituição"}</h1>
      <p className="lead">Conferimos o CNPJ (situação ativa) e o CNES (mesmo CNPJ) nas fontes oficiais. Se conferir, a instituição já aparece verificada e você vira gestor.</p>
      {parentId && <Notice tone="info">Esta unidade ficará abaixo da instituição de onde você veio; seus papéis lá valem aqui.</Notice>}
      <div className="card">
        <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
          <Field id="legalName" label="Razão social" errors={errs("legalName")}>
            {(p) => <input {...p} type="text" value={form.legalName} onChange={set("legalName")} />}
          </Field>
          <Field id="tradeName" label="Nome fantasia" errors={errs("tradeName")}>
            {(p) => <input {...p} type="text" value={form.tradeName} onChange={set("tradeName")} />}
          </Field>
          <Field id="cnpj" label="CNPJ" errors={errs("cnpj")} help="14 dígitos.">
            {(p) => <input {...p} type="text" inputMode="numeric" value={form.cnpj} onChange={set("cnpj")} />}
          </Field>
          <Field id="cnes" label="CNES" errors={errs("cnes")} help="7 dígitos.">
            {(p) => <input {...p} type="text" inputMode="numeric" value={form.cnes} onChange={set("cnes")} />}
          </Field>
          <Field id="type" label="Tipo" errors={errs("type")}>
            {(p) => (
              <select {...p} value={form.type} onChange={set("type")}>
                <option value="NETWORK">Rede</option>
                <option value="HOSPITAL">Hospital</option>
                <option value="UNIT">Unidade</option>
              </select>
            )}
          </Field>
          <Field id="city" label="Cidade" errors={errs("city")}>
            {(p) => <input {...p} type="text" value={form.city} onChange={set("city")} />}
          </Field>
          <Field id="uf" label="UF" errors={errs("uf")}>
            {(p) => (
              <select {...p} value={form.uf} onChange={set("uf")}>
                {UFS.map((u) => (
                  <option key={u}>{u}</option>
                ))}
              </select>
            )}
          </Field>
          <Field id="lat" label="Latitude (opcional)" errors={errs("lat")}>
            {(p) => <input {...p} type="number" step="any" value={form.lat} onChange={set("lat")} />}
          </Field>
          <Field id="lng" label="Longitude (opcional)" errors={errs("lng")}>
            {(p) => <input {...p} type="number" step="any" value={form.lng} onChange={set("lng")} />}
          </Field>
        </div>
        <ProblemAlert error={error} />
        <div className="toolbar">
          <Button type="submit" variant="primary" loading={busy}>
            Cadastrar e conferir
          </Button>
        </div>
      </div>
    </form>
  );
}
