"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/domain";
import { CONTEXTS, NOTE_STATES, ageFrom, groupByLineage, type Composition, type CompositionSummary, type PatientView } from "@/lib/ehr";
import { DOC_STATES, KINDS, type DocumentSummary, type DocumentView, type Kind } from "@/lib/documents";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Field, Loading, ProblemAlert } from "@/components/ui";

/** RF08 · paciente do cofre com as notas (cada nota mostra a versão atual e quantas correções teve). */
export function PatientRecord({ id }: { id: string }) {
  const router = useRouter();
  const [data, setData] = useState<{ patient: PatientView; compositions: CompositionSummary[] } | null>(null);
  const [docs, setDocs] = useState<DocumentSummary[]>([]);
  const [context, setContext] = useState("CONSULTA");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useAsyncEffect(
    async () => {
      const [p, d] = await Promise.all([
        api.get<{ patient: PatientView; compositions: CompositionSummary[] }>(`/api/v1/clinical/patients/${id}`),
        api.get<{ items: DocumentSummary[] }>("/api/v1/clinical/documents", { query: { patientId: id } }),
      ]);
      setData(p);
      setDocs(d.items);
    },
    setError,
    [id],
  );

  async function newDocument(kind: Kind) {
    setBusy(true);
    try {
      const d = await api.post<DocumentView>("/api/v1/clinical/documents", { patientId: id, kind });
      router.push(`/prontuario/documentos/${d.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  async function newNote() {
    setBusy(true);
    try {
      const c = await api.post<Composition>("/api/v1/clinical/compositions", { patientId: id, context });
      router.push(`/prontuario/notas/${c.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  if (!data) return error ? <ProblemAlert error={error} /> : <Loading what="Abrindo o prontuário" />;
  const p = data.patient;
  return (
    <div className="max-w-3xl">
      <p className="result-line">
        <Link href="/prontuario">← Prontuário</Link>
      </p>
      <h1>{p.name}</h1>
      <p className="lead">
        {ageFrom(p.birthDate)} anos · nascimento {formatDate(p.birthDate)}
        {p.cpf ? ` · CPF ${p.cpf}` : ""}
        {p.localRecordNumber ? ` · prontuário ${p.localRecordNumber}` : ""}
      </p>
      <div className="card toolbar">
        <Field id="context" label="Novo registro">
          {(f) => (
            <select {...f} value={context} onChange={(e) => setContext(e.target.value)}>
              {Object.entries(CONTEXTS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Button type="button" variant="primary" loading={busy} onClick={newNote}>
          Nova nota SOAP
        </Button>
        <Button type="button" disabled={busy} onClick={() => newDocument("RECEITA")}>
          Nova receita
        </Button>
        <Button type="button" disabled={busy} onClick={() => newDocument("EXAME")}>
          Novo pedido de exame
        </Button>
      </div>
      <ProblemAlert error={error} />
      <h2>Notas</h2>
      {data.compositions.length === 0 ? (
        <div className="card empty">Nenhuma nota ainda.</div>
      ) : (
        <ul className="feed" aria-label="Notas do paciente">
          {groupByLineage(data.compositions).map((versions) => {
            const current = versions[0]!;
            return (
              <li key={current.lineageId} className="card">
                <Link href={`/prontuario/notas/${current.id}`}>
                  <strong>{CONTEXTS[current.context] ?? current.context}</strong> · {formatDateTime(current.occurredAt)}
                </Link>
                <div className="text-sm text-text-2">
                  {NOTE_STATES[current.state]}
                  {current.icd10.length ? ` · ${current.icd10.join(", ")}` : ""}
                  {versions.length > 1 ? ` · ${versions.length - 1} ${versions.length === 2 ? "correção" : "correções"}` : ""}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <h2>Receitas e pedidos de exame</h2>
      {docs.length === 0 ? (
        <div className="card empty">Nenhum documento ainda.</div>
      ) : (
        <ul className="feed" aria-label="Documentos do paciente">
          {docs.map((d) => (
            <li key={d.id} className="card">
              <Link href={`/prontuario/documentos/${d.id}`}>
                <strong>{KINDS[d.kind]}</strong> · {formatDateTime(d.signedAt ?? d.createdAt)}
              </Link>
              <div className="text-sm text-text-2">
                {DOC_STATES[d.state]} · {d.items} {d.items === 1 ? "item" : "itens"}
                {d.validUntil ? ` · válida até ${formatDate(d.validUntil)}` : ""}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
