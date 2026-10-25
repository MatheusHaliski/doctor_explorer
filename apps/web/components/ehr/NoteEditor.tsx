"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { formatDateTime } from "@/lib/domain";
import { CONTEXTS, NOTE_STATES, isSudoProblem, sameDraft, toDraft, type Code, type Composition, type DraftInput } from "@/lib/ehr";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";
import { Cid10Picker } from "./Cid10Picker";
import { SudoPrompt } from "./SudoPrompt";

const SECTIONS: Array<["subjective" | "objective" | "plan", string, string]> = [
  ["subjective", "S · Subjetivo", "Queixa, história e o que o paciente relata."],
  ["objective", "O · Objetivo", "Exame físico, sinais vitais, resultados."],
  ["plan", "P · Plano", "Conduta, prescrição, exames, retorno."],
];

/**
 * RF08 · nota SOAP. Rascunho salvo sozinho a cada 5 s quando algo mudou; assinar pede sudo; assinada fica só
 * leitura com a verificação de integridade; corrigir abre uma nova versão com motivo.
 */
export function NoteEditor({ id }: { id: string }) {
  const router = useRouter();
  const toast = useToast();
  const [note, setNote] = useState<Composition | null>(null);
  const [draft, setDraft] = useState<DraftInput | null>(null);
  const [codes, setCodes] = useState<Code[]>([]);
  const [snomedText, setSnomedText] = useState("");
  const [saved, setSaved] = useState<{ at: string; draft: DraftInput } | null>(null);
  const [versions, setVersions] = useState<Composition[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [askSudo, setAskSudo] = useState(false);
  const [amending, setAmending] = useState(false);
  const [reason, setReason] = useState("");
  const latest = useRef<{ draft: DraftInput | null; saved: DraftInput | null; editable: boolean }>({ draft: null, saved: null, editable: false });

  const apply = useCallback((c: Composition) => {
    setNote(c);
    const d = toDraft(c);
    setDraft(d);
    setCodes(c.assessment.icd10);
    setSnomedText(c.assessment.snomed.join(", "));
    setSaved({ at: c.updatedAt, draft: d });
  }, []);

  useAsyncEffect(
    async () => {
      const [c, v] = await Promise.all([api.get<Composition>(`/api/v1/clinical/compositions/${id}`), api.get<{ versions: Composition[] }>(`/api/v1/clinical/compositions/${id}/versions`)]);
      apply(c);
      setVersions(v.versions);
    },
    setError,
    [id],
  );

  useEffect(() => {
    latest.current = { draft, saved: saved?.draft ?? null, editable: Boolean(note?.editable) };
  }, [draft, saved, note]);

  const save = useCallback(async (): Promise<boolean> => {
    const { draft: d, saved: s, editable } = latest.current;
    if (!editable || !d || (s && sameDraft(d, s))) return true;
    try {
      const c = await api.put<Composition>(`/api/v1/clinical/compositions/${id}`, d);
      setSaved({ at: c.updatedAt, draft: d });
      setNote(c);
      setError(null);
      return true;
    } catch (err) {
      setError(err);
      return false;
    }
  }, [id]);

  // autosave a cada 5 s (critério 2)
  useEffect(() => {
    const t = setInterval(() => void save(), 5000);
    return () => clearInterval(t);
  }, [save]);

  function update(patch: Partial<DraftInput>) {
    setDraft((d) => (d ? { ...d, ...patch } : d));
  }

  function setIcd(v: Code[]) {
    setCodes(v);
    setDraft((d) => (d ? { ...d, assessment: { ...d.assessment, icd10: v.map((x) => x.code) } } : d));
  }

  async function sign() {
    setBusy("sign");
    setError(null);
    try {
      if (!(await save())) return;
      const c = await api.post<Composition>(`/api/v1/clinical/compositions/${id}/sign`);
      apply(c);
      setAskSudo(false);
      setVersions((await api.get<{ versions: Composition[] }>(`/api/v1/clinical/compositions/${id}/versions`)).versions);
      toast.show("Nota assinada. A partir de agora ela não muda; correções viram nova versão.");
    } catch (err) {
      if (isSudoProblem(err)) setAskSudo(true);
      else setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function amend() {
    setBusy("amend");
    setError(null);
    try {
      const c = await api.post<Composition>(`/api/v1/clinical/compositions/${id}/amend`, { reason });
      router.push(`/prontuario/notas/${c.id}`);
    } catch (err) {
      setError(err);
      setBusy(null);
    }
  }

  async function discard() {
    if (!confirm("Descartar este rascunho? O conteúdo é apagado.")) return;
    try {
      await api.delete(`/api/v1/clinical/compositions/${id}`);
      router.replace(`/prontuario/pacientes/${note!.patientId}`);
    } catch (err) {
      setError(err);
    }
  }

  if (!note || !draft) return error ? <ProblemAlert error={error} /> : <Loading what="Abrindo a nota" />;
  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);
  const dirty = saved ? !sameDraft(draft, saved.draft) : false;
  const ro = !note.editable;

  return (
    <div className="max-w-3xl">
      <p className="result-line">
        <Link href={`/prontuario/pacientes/${note.patientId}`}>← {note.patientName}</Link>
      </p>
      <h1>
        Nota SOAP {note.version > 1 ? `· versão ${note.version}` : ""}
      </h1>
      <p className="lead">
        {CONTEXTS[note.encounter.context] ?? note.encounter.context} em {formatDateTime(note.encounter.occurredAt)} · {NOTE_STATES[note.state]}
        {note.links.some((l) => l.type === "POST") && (
          <>
            {" · "}
            <Link href={`/casos/${note.links.find((l) => l.type === "POST")!.ref}`}>discussão vinculada</Link>
          </>
        )}
      </p>
      {note.amendReason && <Notice tone="info">Motivo da correção: {note.amendReason}</Notice>}
      {note.state === "RETIFICADA" && note.supersededBy && (
        <Notice tone="warning">
          Esta versão foi corrigida. <Link href={`/prontuario/notas/${note.supersededBy}`}>Abrir a versão atual</Link>.
        </Notice>
      )}
      {note.signedAt && (
        <Notice tone={note.integrity?.valid ? "success" : "danger"}>
          Assinada por {note.author.name}
          {note.author.crm ? ` (CRM ${note.author.crm}/${note.author.uf})` : ""} em {formatDateTime(note.signedAt)}.{" "}
          {note.integrity?.valid ? "Integridade conferida agora: o conteúdo é o mesmo que foi assinado." : "Atenção: o conteúdo não confere com a assinatura."}
        </Notice>
      )}

      <form onSubmit={(e) => e.preventDefault()} noValidate>
        {SECTIONS.slice(0, 2).map(([key, label, help]) => (
          <Field key={key} id={key} label={label} help={help} errors={errs(key)}>
            {(p) => <textarea {...p} rows={5} readOnly={ro} value={draft[key]} onChange={(e) => update({ [key]: e.target.value })} />}
          </Field>
        ))}
        <fieldset className="card mt-2">
          <legend className="font-bold">A · Avaliação</legend>
          <Field id="assessment" label="Impressão clínica" errors={errs("assessment.text")}>
            {(p) => <textarea {...p} rows={3} readOnly={ro} value={draft.assessment.text} onChange={(e) => update({ assessment: { ...draft.assessment, text: e.target.value } })} />}
          </Field>
          <p className="mb-1 font-bold">CID-10</p>
          <Cid10Picker value={codes} onChange={setIcd} disabled={ro} />
          {errs("assessment.icd10").map((m) => (
            <p key={m} className="field-error">
              {m}
            </p>
          ))}
          <Field id="snomed" label="SNOMED CT (opcional)" help="Códigos separados por vírgula." errors={errs("assessment.snomed")}>
            {(p) => (
              <input
                {...p}
                type="text"
                readOnly={ro}
                value={snomedText}
                onChange={(e) => {
                  setSnomedText(e.target.value);
                  update({ assessment: { ...draft.assessment, snomed: e.target.value.split(/[,\s]+/).filter(Boolean) } });
                }}
              />
            )}
          </Field>
        </fieldset>
        {SECTIONS.slice(2).map(([key, label, help]) => (
          <Field key={key} id={key} label={label} help={help} errors={errs(key)}>
            {(p) => <textarea {...p} rows={4} readOnly={ro} value={draft[key]} onChange={(e) => update({ [key]: e.target.value })} />}
          </Field>
        ))}
      </form>

      {note.editable && (
        <p role="status" aria-live="polite" className="text-sm text-text-2">
          {dirty ? "Alterações ainda não salvas (salva sozinho a cada 5 s)." : `Rascunho salvo ${saved ? formatDateTime(saved.at) : ""}.`}
        </p>
      )}
      <ProblemAlert error={error} />
      {askSudo && <SudoPrompt onConfirmed={sign} onCancel={() => setAskSudo(false)} />}

      <div className="toolbar">
        {note.editable && (
          <>
            <Button type="button" variant="primary" loading={busy === "sign"} onClick={sign}>
              Assinar nota
            </Button>
            <Button type="button" onClick={() => void save()} disabled={!dirty}>
              Salvar agora
            </Button>
            <Button type="button" variant="danger" onClick={discard}>
              Descartar rascunho
            </Button>
          </>
        )}
        {note.amendable && !amending && (
          <Button type="button" onClick={() => setAmending(true)}>
            Corrigir (nova versão)
          </Button>
        )}
      </div>
      {amending && (
        <div className="card">
          <Field id="reason" label="Motivo da correção" help="Fica junto da nova versão; a versão assinada continua visível." errors={errs("reason")}>
            {(p) => <textarea {...p} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />}
          </Field>
          <div className="toolbar">
            <Button type="button" variant="primary" loading={busy === "amend"} disabled={reason.trim().length < 10} onClick={amend}>
              Abrir nova versão
            </Button>
            <Button type="button" onClick={() => setAmending(false)}>
              Cancelar
            </Button>
          </div>
        </div>
      )}

      {versions.length > 1 && (
        <section aria-labelledby="versions-title">
          <h2 id="versions-title">Versões desta nota</h2>
          <ol className="text-sm">
            {versions.map((v) => (
              <li key={v.id}>
                {v.id === note.id ? <strong>Versão {v.version}</strong> : <Link href={`/prontuario/notas/${v.id}`}>Versão {v.version}</Link>} · {NOTE_STATES[v.state]}
                {v.signedAt ? ` · assinada ${formatDateTime(v.signedAt)}` : ""}
                {v.amendReason ? ` · motivo: ${v.amendReason}` : ""}
              </li>
            ))}
          </ol>
        </section>
      )}
      {note.contentHash && (
        <details className="mt-4 text-sm text-text-2">
          <summary>Dados técnicos da assinatura</summary>
          <p className="break-all">
            SHA-256 do conteúdo: {note.contentHash}
            <br />
            Chave: {note.signingKeyId} ({note.integrity?.algorithm})
          </p>
        </details>
      )}
      {toast.el}
    </div>
  );
}
