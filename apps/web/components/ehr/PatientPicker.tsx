"use client";

import { useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { ageFrom, type PatientView } from "@/lib/ehr";
import { formatDate } from "@/lib/domain";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Field, ProblemAlert } from "@/components/ui";

/**
 * Busca no cofre (índice cego: o servidor nunca vê o nome em claro fora do cofre) ou cadastro mínimo do paciente.
 * Sem busca, lista os pacientes com registro mais recente.
 */
export function PatientPicker({ onPick, actionLabel }: { onPick: (p: PatientView) => void; actionLabel: string }) {
  const [q, setQ] = useState("");
  const [applied, setApplied] = useState("");
  const [items, setItems] = useState<PatientView[] | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: "", birthDate: "", cpf: "", sex: "", localRecordNumber: "" });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useAsyncEffect(
    async () => {
      setError(null);
      const r = await api.get<{ items: PatientView[] }>("/api/v1/clinical/patients", { query: { q: applied } });
      setItems(r.items);
    },
    setError,
    [applied],
  );

  function search(e: FormEvent) {
    e.preventDefault();
    setApplied(q.trim());
  }

  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const p = await api.post<PatientView>("/api/v1/clinical/patients", { ...form, sex: form.sex || null, cpf: form.cpf || null });
      onPick(p);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);

  return (
    <div>
      <form onSubmit={search} className="toolbar" role="search">
        <label className="sr-only" htmlFor="patient-q">
          Buscar paciente no cofre
        </label>
        <input id="patient-q" type="search" placeholder="Nome do paciente (ex.: maria silva)" value={q} onChange={(e) => setQ(e.target.value)} className="input min-w-0 flex-1" />
        <Button type="submit">Buscar</Button>
        <Button type="button" variant="primary" onClick={() => setCreating((v) => !v)} aria-expanded={creating}>
          Novo paciente
        </Button>
      </form>
      {creating && (
        <form onSubmit={create} className="card" noValidate aria-labelledby="new-patient">
          <h2 id="new-patient" className="mt-0 text-md">
            Cadastro mínimo
          </h2>
          <p className="text-sm text-text-2">Fica só no seu cofre clínico, cifrado. Nada disso vai para o fórum.</p>
          <Field id="name" label="Nome completo" errors={errs("name")}>
            {(p) => <input {...p} type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} autoComplete="off" />}
          </Field>
          <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))" }}>
            <Field id="birthDate" label="Nascimento" errors={errs("birthDate")}>
              {(p) => <input {...p} type="date" value={form.birthDate} onChange={(e) => setForm({ ...form, birthDate: e.target.value })} />}
            </Field>
            <Field id="cpf" label="CPF (opcional)" errors={errs("cpf")}>
              {(p) => <input {...p} type="text" inputMode="numeric" value={form.cpf} onChange={(e) => setForm({ ...form, cpf: e.target.value })} autoComplete="off" />}
            </Field>
            <Field id="sex" label="Sexo" errors={errs("sex")}>
              {(p) => (
                <select {...p} value={form.sex} onChange={(e) => setForm({ ...form, sex: e.target.value })}>
                  <option value="">Não informado</option>
                  <option value="female">Feminino</option>
                  <option value="male">Masculino</option>
                  <option value="other">Outro</option>
                </select>
              )}
            </Field>
            <Field id="localRecordNumber" label="Nº do prontuário local (opcional)" errors={errs("localRecordNumber")}>
              {(p) => <input {...p} type="text" value={form.localRecordNumber} onChange={(e) => setForm({ ...form, localRecordNumber: e.target.value })} autoComplete="off" />}
            </Field>
          </div>
          <div className="toolbar">
            <Button type="submit" variant="primary" loading={busy}>
              Cadastrar e {actionLabel.toLowerCase()}
            </Button>
          </div>
        </form>
      )}
      <ProblemAlert error={error} />
      {items === null ? (
        <p className="text-sm text-text-2">Carregando o cofre…</p>
      ) : items.length === 0 ? (
        <div className="card empty">{applied ? "Nenhum paciente com esse nome no seu cofre." : "Seu cofre ainda não tem pacientes."}</div>
      ) : (
        <ul className="patient-list" aria-label={applied ? "Resultados da busca" : "Pacientes recentes"}>
          {items.map((p) => (
            <li key={p.id} className="card">
              <div className="min-w-0">
                <strong>{p.name}</strong>
                <div className="text-sm text-text-2">
                  {ageFrom(p.birthDate)} anos · nascimento {formatDate(p.birthDate)}
                  {p.localRecordNumber ? ` · prontuário ${p.localRecordNumber}` : ""}
                </div>
              </div>
              <Button type="button" onClick={() => onPick(p)}>
                {actionLabel}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
