"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/domain";
import { KINDS, normalizeCode, type PublicView } from "@/lib/documents";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Field, Loading, Notice, ProblemAlert } from "@/components/ui";

export function CodeForm({ initial = "" }: { initial?: string }) {
  const router = useRouter();
  const [code, setCode] = useState(initial);
  const [invalid, setInvalid] = useState(false);

  function submit(e: FormEvent) {
    e.preventDefault();
    const c = normalizeCode(code);
    setInvalid(!c);
    if (c) router.push(`/validar/${c}`);
  }

  return (
    <form onSubmit={submit} noValidate>
      <Field id="code" label="Código de validação" help="16 letras e números, no formato XXXX-XXXX-XXXX-XXXX." errors={invalid ? ["Código incompleto ou com caracteres inválidos."] : []}>
        {(p) => <input {...p} className="input mono" autoComplete="off" autoCapitalize="characters" spellCheck={false} value={code} onChange={(e) => setCode(e.target.value)} />}
      </Field>
      <Button type="submit" variant="primary">
        Validar
      </Button>
    </form>
  );
}

const TONE: Record<PublicView["status"], "success" | "danger" | "warning"> = { VALIDA: "success", EXPIRADA: "warning", CANCELADA: "danger", ASSINATURA_INVALIDA: "danger" };
const HEADLINE: Record<PublicView["status"], string> = {
  VALIDA: "Documento válido",
  EXPIRADA: "Documento fora da validade",
  CANCELADA: "Documento cancelado pelo médico",
  ASSINATURA_INVALIDA: "Assinatura não confere",
};

/** RF09 · o que a farmácia precisa para dispensar: situação, médico, iniciais do paciente e itens. Nada além. */
export function Validator({ code }: { code: string }) {
  const normalized = normalizeCode(code);
  const [view, setView] = useState<PublicView | null>(null);
  const [error, setError] = useState<unknown>(null);

  useAsyncEffect(
    async () => {
      if (!normalized) return;
      setView(await api.get<PublicView>(`/api/v1/public/documents/${normalized}`));
    },
    setError,
    [normalized],
  );

  if (!normalized || error) {
    return (
      <>
        <h1 className="text-lg">Validar documento médico</h1>
        {normalized ? <ProblemAlert error={error} /> : <Notice tone="danger">Código inválido.</Notice>}
        <CodeForm initial={code} />
      </>
    );
  }
  if (!view) return <Loading what="Conferindo a assinatura" />;
  return (
    <>
      <h1 className="text-lg">{KINDS[view.kind]}</h1>
      <Notice tone={TONE[view.status]} role="status">
        <strong>{HEADLINE[view.status]}.</strong> {view.statusText}
      </Notice>
      {view.simulated && (
        <Notice tone="warning">Assinado com a ICP-Brasil simulada do ambiente de desenvolvimento: não tem validade jurídica e não deve ser dispensado.</Notice>
      )}
      <dl className="doc-facts mt-4">
        <dt>Código</dt>
        <dd className="mono">{normalized}</dd>
        <dt>Emitido em</dt>
        <dd>{formatDateTime(view.issuedAt)}</dd>
        {view.validUntil && (
          <>
            <dt>Válido até</dt>
            <dd>{formatDate(view.validUntil)}</dd>
          </>
        )}
        <dt>Médico</dt>
        <dd>
          {view.doctorName} · CRM {view.crm}/{view.uf}
        </dd>
        <dt>Paciente</dt>
        <dd>{view.patientInitials}</dd>
        <dt>Assinatura</dt>
        <dd>{view.signatureValid ? `${view.signatureLevel}, conferida agora` : "não confere"}</dd>
        <dt>SHA-256 do PDF</dt>
        <dd className="mono">{view.pdfSha256}</dd>
      </dl>
      <h2 className="text-md">Itens</h2>
      <ol className="doc-summary">
        {view.items.map((i) => (
          <li key={i}>{i}</li>
        ))}
      </ol>
      <p className="text-sm text-text-3">
        Confira se o SHA-256 do PDF que você recebeu é o mesmo daqui. <Link href="/validar">Validar outro código</Link>
      </p>
    </>
  );
}
