"use client";

import Link from "next/link";
import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/domain";
import { CHANNELS, type Channel, type DocumentView } from "@/lib/documents";
import { Button, Field, Notice, ProblemAlert, useToast } from "@/components/ui";

/** Salva um arquivo vindo da API sem deixá-lo em cache nem em URL permanente. */
export function saveFile(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Documento assinado: código de validação, PDF, envio ao paciente (link de uso único, 7 dias) e cancelamento. */
export function SignedDocument({ doc, onChange }: { doc: DocumentView; onChange: (d: DocumentView) => void }) {
  const toast = useToast();
  const [channel, setChannel] = useState<Channel>("EMAIL");
  const [destination, setDestination] = useState("");
  const [link, setLink] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const canceled = doc.state === "CANCELADA";
  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);

  async function download() {
    setBusy("pdf");
    setError(null);
    try {
      saveFile(await api.file("GET", `/api/v1/clinical/documents/${doc.id}/pdf`), `${doc.kind === "RECEITA" ? "receita" : "pedido-exame"}-${doc.validationCode}.pdf`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function deliver() {
    setBusy("deliver");
    setError(null);
    try {
      const r = await api.post<{ link: string; channel: Channel; destination: string | null }>(`/api/v1/clinical/documents/${doc.id}/deliver`, {
        channel,
        destination: channel === "LINK" ? null : destination,
      });
      setLink(r.link);
      setDestination("");
      onChange(await api.get<DocumentView>(`/api/v1/clinical/documents/${doc.id}`));
      toast.show(channel === "LINK" ? "Link gerado." : `Enviado para ${r.destination}.`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function cancel() {
    if (!confirm("Cancelar este documento? A farmácia passa a ver “cancelado” no validador e os links do paciente deixam de abrir.")) return;
    setBusy("cancel");
    setError(null);
    try {
      onChange(await api.post<DocumentView>(`/api/v1/clinical/documents/${doc.id}/cancel`, { reason }));
      setLink(null);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      {canceled ? (
        <Notice tone="danger" role="status">
          Cancelado em {formatDateTime(doc.canceledAt!)}. Motivo: {doc.cancelReason}
        </Notice>
      ) : (
        <Notice tone="success" role="status">
          Assinado com {doc.signatureLevel} em {formatDateTime(doc.signedAt!)}.
          {doc.validUntil ? ` Válido até ${formatDate(doc.validUntil)}.` : ""}
        </Notice>
      )}
      <section className="card mt-4" aria-labelledby="sig-title">
        <h2 id="sig-title" className="mt-0 text-md">
          Validação
        </h2>
        <p className="doc-code" aria-label="Código de validação">
          {doc.validationCode}
        </p>
        <p className="text-sm text-text-2">
          A farmácia confere pelo QR do PDF ou em{" "}
          <Link href={`/validar/${doc.validationCode}`} target="_blank">
            /validar/{doc.validationCode}
          </Link>
          .
        </p>
        <dl className="doc-facts">
          <dt>Certificado</dt>
          <dd>{doc.certSubject}</dd>
          <dt>Provedor</dt>
          <dd>
            {doc.signatureProvider === "SIMULADO" ? "ICP-Brasil simulada (ambiente de desenvolvimento: sem validade jurídica)" : doc.signatureProvider}
          </dd>
          <dt>SHA-256 do PDF</dt>
          <dd className="mono">{doc.pdfSha256}</dd>
        </dl>
        <ProblemAlert error={error} />
        <div className="toolbar">
          <Button type="button" variant="primary" loading={busy === "pdf"} onClick={download}>
            Baixar PDF assinado
          </Button>
        </div>
      </section>

      {!canceled && (
        <section className="card mt-4" aria-labelledby="deliver-title">
          <h2 id="deliver-title" className="mt-0 text-md">
            Enviar ao paciente
          </h2>
          <p className="text-sm text-text-2">O paciente recebe um link que abre o PDF uma única vez, por até 7 dias. A mensagem não leva nome, medicamento nem diagnóstico.</p>
          <div className="segmented" role="radiogroup" aria-label="Canal">
            {(Object.keys(CHANNELS) as Channel[]).map((c) => (
              <label key={c}>
                <input type="radio" name="channel" checked={channel === c} onChange={() => setChannel(c)} />
                {CHANNELS[c]}
              </label>
            ))}
          </div>
          {channel !== "LINK" && (
            <Field id="destination" label={channel === "EMAIL" ? "E-mail do paciente" : "Celular com DDD"} errors={errs("destination")}>
              {(p) => (
                <input
                  {...p}
                  className="input"
                  type={channel === "EMAIL" ? "email" : "tel"}
                  autoComplete="off"
                  value={destination}
                  onChange={(e) => setDestination(e.target.value)}
                />
              )}
            </Field>
          )}
          <div className="toolbar">
            <Button type="button" variant="primary" loading={busy === "deliver"} disabled={channel !== "LINK" && !destination.trim()} onClick={deliver}>
              {channel === "LINK" ? "Gerar link" : "Enviar"}
            </Button>
          </div>
          {link && (
            <div className="mt-4">
              <Field id="patient-link" label="Link de uso único" help="Mostrado só agora. Se perder, gere outro.">
                {(p) => <input {...p} className="input mono" readOnly value={link} onFocus={(e) => e.target.select()} />}
              </Field>
              <Button
                type="button"
                size="sm"
                onClick={async () => {
                  await navigator.clipboard?.writeText(link);
                  toast.show("Link copiado.");
                }}
              >
                Copiar link
              </Button>
            </div>
          )}
        </section>
      )}

      {doc.deliveries.length > 0 && (
        <section className="card mt-4" aria-labelledby="deliveries-title">
          <h2 id="deliveries-title" className="mt-0 text-md">
            Envios
          </h2>
          <table className="data">
            <thead>
              <tr>
                <th>Canal</th>
                <th>Destino</th>
                <th>Expira</th>
                <th>Aberto</th>
              </tr>
            </thead>
            <tbody>
              {doc.deliveries.map((d) => (
                <tr key={d.createdAt}>
                  <td>{CHANNELS[d.channel]}</td>
                  <td>{d.destination ?? "—"}</td>
                  <td>{formatDateTime(d.expiresAt)}</td>
                  <td>{d.usedAt ? formatDateTime(d.usedAt) : "ainda não"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {!canceled && (
        <details className="card mt-4">
          <summary>Cancelar documento</summary>
          <p className="text-sm text-text-2">Use se houve erro. O PDF continua íntegro, mas o validador passa a mostrar “cancelado”. Para corrigir, emita um novo documento.</p>
          <Field id="cancel-reason" label="Motivo" errors={errs("reason")} help="Pelo menos 10 caracteres. Fica no prontuário e na auditoria, não aparece para a farmácia.">
            {(p) => <textarea {...p} rows={2} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />}
          </Field>
          <Button type="button" variant="danger" loading={busy === "cancel"} disabled={reason.trim().length < 10} onClick={cancel}>
            Cancelar documento
          </Button>
        </details>
      )}
    </>
  );
}
