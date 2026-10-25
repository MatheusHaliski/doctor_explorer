"use client";

import { useState } from "react";
import { api } from "@/lib/api";
import { Button, Notice, ProblemAlert } from "@/components/ui";
import { saveFile } from "@/components/documents/SignedDocument";

/**
 * RF09 · link do paciente. O PDF só sai ao clicar (POST com CSRF): pré-visualizadores de e-mail e antivírus que
 * abrem o link não gastam o uso único.
 */
export function PatientDownload({ token }: { token: string }) {
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function open() {
    setBusy(true);
    setError(null);
    try {
      saveFile(await api.file("POST", `/api/v1/public/deliveries/${encodeURIComponent(token)}`), "documento-medico.pdf");
      setDone(true);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1 className="text-lg">Seu documento médico</h1>
      {done ? (
        <Notice tone="success" role="status">
          Pronto: o PDF foi baixado. Este link não abre de novo; guarde o arquivo. A farmácia confere a autenticidade pelo QR impresso.
        </Notice>
      ) : (
        <>
          <p className="text-sm text-text-2">Seu médico enviou uma receita ou um pedido de exame assinado digitalmente. O download funciona uma única vez.</p>
          <ProblemAlert error={error} />
          <Button type="button" variant="primary" loading={busy} onClick={open}>
            Baixar PDF
          </Button>
        </>
      )}
    </>
  );
}
