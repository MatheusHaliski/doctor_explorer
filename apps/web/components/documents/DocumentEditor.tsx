"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { formatDateTime } from "@/lib/domain";
import { isSudoProblem } from "@/lib/ehr";
import {
  DOC_STATES,
  KINDS,
  STEPS,
  newMedication,
  sameContent,
  stepOf,
  toContent,
  type CatalogExam,
  type CatalogMedication,
  type Content,
  type DocumentView,
  type Template,
} from "@/lib/documents";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";
import { SudoPrompt } from "@/components/ehr/SudoPrompt";
import { ExamSearch, MedicationSearch } from "./CatalogSearch";
import { MedicationItem } from "./MedicationItem";
import { SignedDocument } from "./SignedDocument";

type Meta = Record<string, { label: string; antimicrobial: boolean }>;

/**
 * RF09 · receita ou pedido de exame. Itens → revisão (resumo como vai no PDF, com o hash do que foi revisto) →
 * assinatura com sudo (PAdES) → entrega. Assinado não muda: só cancelar com motivo.
 */
export function DocumentEditor({ id }: { id: string }) {
  const toast = useToast();
  const [doc, setDoc] = useState<DocumentView | null>(null);
  const [content, setContent] = useState<Content | null>(null);
  const [meta, setMeta] = useState<Meta>({});
  const [exams, setExams] = useState<Record<string, CatalogExam>>({});
  const [reviewing, setReviewing] = useState(false);
  const [checked, setChecked] = useState(false);
  const [askSudo, setAskSudo] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [templateName, setTemplateName] = useState("");

  function apply(d: DocumentView) {
    setDoc(d);
    setContent(toContent(d));
    setMeta((m) => ({ ...m, ...Object.fromEntries(d.medications.map((x) => [x.input.ggrem, { label: x.label, antimicrobial: x.antimicrobial }])) }));
    setExams((m) => ({ ...m, ...Object.fromEntries(d.exams.map((x) => [x.loinc, x])) }));
  }

  useAsyncEffect(
    async () => {
      const d = await api.get<DocumentView>(`/api/v1/clinical/documents/${id}`);
      apply(d);
      if (d.state === "RASCUNHO") setTemplates((await api.get<{ items: Template[] }>("/api/v1/clinical/document-templates", { query: { kind: d.kind } })).items);
    },
    setError,
    [id],
  );

  // assinatura em andamento (outra aba ou provedor lento): confere de novo a cada 3 s
  useEffect(() => {
    if (doc?.state !== "ASSINANDO") return;
    const t = setInterval(async () => {
      try {
        apply(await api.get<DocumentView>(`/api/v1/clinical/documents/${id}`));
      } catch {
        /* tenta no próximo ciclo */
      }
    }, 3000);
    return () => clearInterval(t);
  }, [doc?.state, id]);

  if (!doc || !content) return error ? <ProblemAlert error={error} /> : <Loading what="Abrindo o documento" />;
  const fieldErrors = (f: string) => (error instanceof ApiError ? error.field(f) : []);
  const step = stepOf(doc.state, reviewing);
  const rx = doc.kind === "RECEITA";
  const dirty = !sameContent(content, toContent(doc));
  const antimicrobial = doc.medications.some((m) => m.antimicrobial);

  async function review() {
    setBusy("review");
    setError(null);
    try {
      apply(await api.put<DocumentView>(`/api/v1/clinical/documents/${id}`, content));
      setReviewing(true);
      setChecked(false);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function sign() {
    setBusy("sign");
    setError(null);
    try {
      const d = await api.post<DocumentView>(`/api/v1/clinical/documents/${id}/sign`, { reviewedHash: doc!.contentHash });
      apply(d);
      setAskSudo(false);
      if (d.state === "RASCUNHO") setReviewing(true);
      else toast.show(rx ? "Receita assinada." : "Pedido de exame assinado.");
    } catch (err) {
      if (isSudoProblem(err)) setAskSudo(true);
      else setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function saveTemplate() {
    setBusy("template");
    setError(null);
    try {
      await api.post("/api/v1/clinical/document-templates", { kind: doc!.kind, name: templateName, content });
      setTemplates((await api.get<{ items: Template[] }>("/api/v1/clinical/document-templates", { query: { kind: doc!.kind } })).items);
      setTemplateName("");
      toast.show("Modelo salvo. Ele guarda só os itens, nunca o paciente.");
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  function applyTemplate(tid: string) {
    const t = templates.find((x) => x.id === tid);
    if (!t) return;
    setContent({ ...t.content, notes: content!.notes, indication: t.content.indication || content!.indication });
    // rótulos chegam do servidor ao revisar; até lá mostra o código
    setMeta((m) => ({ ...Object.fromEntries(t.content.medications.map((x) => [x.ggrem, { label: `GGREM ${x.ggrem}`, antimicrobial: false }])), ...m }));
  }

  const addMedication = (m: CatalogMedication) => {
    setMeta((x) => ({ ...x, [m.ggrem]: { label: `${m.substance} · ${m.presentation} (${m.product})`, antimicrobial: m.antimicrobial } }));
    setContent({ ...content, medications: [...content.medications, newMedication(m)] });
  };
  const addExam = (e: CatalogExam) => {
    setExams((x) => ({ ...x, [e.loinc]: e }));
    setContent({ ...content, exams: [...content.exams, { loinc: e.loinc }] });
  };

  return (
    <div className="max-w-3xl">
      <p className="result-line">
        <Link href={`/prontuario/pacientes/${doc.patientId}`}>← {doc.patientName}</Link>
      </p>
      <h1>
        {KINDS[doc.kind]} · {doc.patientName}
      </h1>
      <p className="lead">
        {DOC_STATES[doc.state]}
        {doc.signedAt ? ` em ${formatDateTime(doc.signedAt)}` : ` · criado em ${formatDateTime(doc.createdAt)}`}
      </p>
      <ol className="steps" aria-label="Etapas">
        {STEPS.map((s, i) => (
          <li key={s} aria-current={s === step ? "step" : undefined} className={STEPS.indexOf(step) > i ? "done" : undefined}>
            <span>{i + 1}</span> {s}
          </li>
        ))}
      </ol>

      {doc.failureReason && doc.state === "RASCUNHO" && (
        <Notice tone="danger" role="alert">
          A última tentativa de assinatura não foi concluída: {doc.failureReason} O rascunho está intacto; tente de novo.
        </Notice>
      )}

      {step === "Itens" && (
        <>
          {templates.length > 0 && (
            <div className="card toolbar">
              <Field id="tpl" label="Começar de um modelo seu">
                {(p) => (
                  <select {...p} defaultValue="" onChange={(e) => applyTemplate(e.target.value)}>
                    <option value="" disabled>
                      Escolha…
                    </option>
                    {templates.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                )}
              </Field>
            </div>
          )}
          <section className="card mt-4" aria-labelledby="items-title">
            <h2 id="items-title" className="mt-0 text-md">
              {rx ? "Medicamentos" : "Exames"}
            </h2>
            {[...fieldErrors("medications"), ...fieldErrors("exams"), ...fieldErrors("items")].map((m) => (
              <p key={m} className="field-error" role="alert">
                {m}
              </p>
            ))}
            {rx ? (
              <>
                <ul className="doc-items" aria-label="Medicamentos da receita">
                  {content.medications.map((m, i) => (
                    <MedicationItem
                      key={`${m.ggrem}-${i}`}
                      index={i}
                      value={m}
                      label={meta[m.ggrem]?.label ?? m.ggrem}
                      antimicrobial={meta[m.ggrem]?.antimicrobial ?? false}
                      errors={fieldErrors}
                      onChange={(v) => setContent({ ...content, medications: content.medications.map((x, j) => (j === i ? v : x)) })}
                      onRemove={() => setContent({ ...content, medications: content.medications.filter((_, j) => j !== i) })}
                    />
                  ))}
                </ul>
                <MedicationSearch onPick={addMedication} taken={content.medications.map((m) => m.ggrem)} />
              </>
            ) : (
              <>
                <ul className="chips" aria-label="Exames do pedido">
                  {content.exams.map((e, i) => (
                    <li key={e.loinc}>
                      <span>
                        <strong>{exams[e.loinc]?.name ?? e.loinc}</strong> · TUSS {exams[e.loinc]?.tuss ?? "?"}
                      </span>
                      <button type="button" aria-label={`Remover ${exams[e.loinc]?.name ?? e.loinc}`} onClick={() => setContent({ ...content, exams: content.exams.filter((_, j) => j !== i) })}>
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
                <ExamSearch onPick={addExam} taken={content.exams.map((e) => e.loinc)} />
                <Field id="indication" label="Indicação clínica" errors={fieldErrors("indication")} help="Obrigatória para assinar. Vai impressa no pedido.">
                  {(p) => <textarea {...p} rows={2} maxLength={500} value={content.indication} onChange={(e) => setContent({ ...content, indication: e.target.value })} />}
                </Field>
              </>
            )}
            <Field id="notes" label="Observações (opcional)" errors={fieldErrors("notes")} help="Vão impressas no documento. Até 500 caracteres.">
              {(p) => <textarea {...p} rows={2} maxLength={500} value={content.notes} onChange={(e) => setContent({ ...content, notes: e.target.value })} />}
            </Field>
          </section>
          <ProblemAlert error={error} />
          <div className="toolbar mt-4">
            <Button type="button" variant="primary" loading={busy === "review"} disabled={(rx ? content.medications.length : content.exams.length) === 0} onClick={review}>
              Salvar e revisar
            </Button>
            <Field id="tpl-name" label="Salvar itens como modelo">
              {(p) => <input {...p} className="input" placeholder="Ex.: Sinusite aguda" value={templateName} onChange={(e) => setTemplateName(e.target.value)} />}
            </Field>
            <Button type="button" size="sm" loading={busy === "template"} disabled={templateName.trim().length < 3} onClick={saveTemplate}>
              Salvar modelo
            </Button>
          </div>
        </>
      )}

      {step === "Revisão" && (
        <section className="card mt-4" aria-labelledby="review-title">
          <h2 id="review-title" className="mt-0 text-md">
            Confira antes de assinar
          </h2>
          <p className="text-sm text-text-2">É exatamente isto que vai no PDF. Depois de assinado, o documento não muda.</p>
          <ol className="doc-summary">
            {doc.medications.map((m) => (
              <li key={m.input.ggrem}>
                <strong>{m.label}</strong>
                <div>{m.posology}</div>
                {m.input.quantity && <div className="text-sm text-text-2">Quantidade: {m.input.quantity}</div>}
              </li>
            ))}
            {doc.exams.map((e) => (
              <li key={e.loinc}>
                <strong>{e.name}</strong>
                <div className="text-sm text-text-2">
                  TUSS {e.tuss} · LOINC {e.loinc}
                </div>
              </li>
            ))}
          </ol>
          {!rx && (
            <p>
              <strong>Indicação clínica:</strong> {doc.indication || <em>não informada (obrigatória)</em>}
            </p>
          )}
          {doc.notes && (
            <p>
              <strong>Observações:</strong> {doc.notes}
            </p>
          )}
          {antimicrobial && (
            <Notice tone="info">Há antimicrobiano: a receita sai em 2 vias (a farmácia retém a segunda) e vale 10 dias a partir de hoje (RDC 471/2021).</Notice>
          )}
          <p className="text-sm text-text-3">
            Revisão <span className="mono">{doc.contentHash.slice(0, 16)}</span>: se algo mudar depois daqui, a assinatura pede nova revisão.
          </p>
          <label className="switch">
            <input type="checkbox" checked={checked} onChange={(e) => setChecked(e.target.checked)} />
            Conferi paciente, itens e posologia
          </label>
          <ProblemAlert error={error} />
          {askSudo ? (
            <SudoPrompt onConfirmed={sign} onCancel={() => setAskSudo(false)} />
          ) : (
            <div className="toolbar">
              <Button type="button" variant="primary" disabled={!checked} loading={busy === "sign"} onClick={sign}>
                Assinar com certificado ICP-Brasil
              </Button>
              <Button type="button" onClick={() => setReviewing(false)}>
                Voltar e editar
              </Button>
            </div>
          )}
        </section>
      )}

      {step === "Assinatura" && (
        <Notice tone="info" role="status">
          Assinando: gerando o PDF/A, aplicando a assinatura e o carimbo de tempo. Isto leva alguns segundos.
        </Notice>
      )}

      {step === "Entrega" && <SignedDocument doc={doc} onChange={apply} />}
      {dirty && step === "Itens" && <p className="text-sm text-text-3">Alterações ainda não salvas: “Salvar e revisar” grava o rascunho.</p>}
      {toast.el}
    </div>
  );
}
