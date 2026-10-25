"use client";

import { useState, type FormEvent } from "react";
import { api } from "@/lib/api";
import { Button, Field, ProblemAlert } from "@/components/ui";

/** Confirma a identidade (senha e, se houver, código do autenticador) antes de assinar. Vale 10 minutos. */
export function SudoPrompt({ onConfirmed, onCancel }: { onConfirmed: () => void; onCancel: () => void }) {
  const [form, setForm] = useState({ password: "", code: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.post("/api/v1/me/sudo", form);
      onConfirmed();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="card mt-4" aria-labelledby="sudo-title" noValidate>
      <h2 id="sudo-title" className="mt-0 text-md">
        Confirme sua identidade para assinar
      </h2>
      <p className="text-sm text-text-2">A assinatura usa sua sessão, esta confirmação e o carimbo de tempo do servidor. A nota assinada não pode mais ser alterada.</p>
      <Field id="sudo-password" label="Senha">
        {(p) => <input {...p} type="password" autoComplete="current-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />}
      </Field>
      <Field id="sudo-code" label="Código do autenticador" help="Só se o 2FA estiver ativo.">
        {(p) => <input {...p} type="text" inputMode="numeric" autoComplete="one-time-code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />}
      </Field>
      <ProblemAlert error={error} />
      <div className="toolbar">
        <Button type="submit" variant="primary" loading={busy} disabled={!form.password}>
          Confirmar e assinar
        </Button>
        <Button type="button" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
