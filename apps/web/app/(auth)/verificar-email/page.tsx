"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { Button, Field, Notice, ProblemAlert } from "@/components/ui";

type Me = { email: string; status: string; emailVerified: boolean };

/** RF01 · confirmação do e-mail com código de 6 dígitos (30 min, 5 tentativas, reenvio 1/min). */
export default function VerifyEmailPage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [resent, setResent] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<Me>("/api/v1/me")
      .then((m) => {
        if (m.status !== "PENDING_VERIFICATION") router.replace("/feed");
        else setMe(m);
      })
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) router.replace("/login");
        else setError(e);
      });
  }, [router]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.post("/api/v1/me/verify-email", { code: code.trim() });
      router.replace("/perfil?novo=1");
      router.refresh();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setError(null);
    setResent(null);
    try {
      await api.post("/api/v1/me/verify-email/resend");
      setResent("Código reenviado. Confira a caixa de entrada e o spam.");
    } catch (err) {
      setError(err);
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <h1 className="text-center">Confirme seu e-mail</h1>
      <p className="lead text-center">Enviamos um código de 6 dígitos para {me?.email ?? "seu e-mail"}. Ele vale por 30 minutos.</p>
      <Field id="code" label="Código de confirmação" help="Até 5 tentativas; depois peça um novo código.">
        {(p) => <input {...p} type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={(e) => setCode(e.target.value)} />}
      </Field>
      {resent && <Notice tone="success">{resent}</Notice>}
      <ProblemAlert error={error} />
      <div className="toolbar">
        <Button type="submit" variant="primary" loading={busy} className="w-full">
          Confirmar
        </Button>
        <Button type="button" className="w-full" onClick={resend}>
          Reenviar código
        </Button>
      </div>
    </form>
  );
}
