"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { Button, Field, Notice, ProblemAlert } from "@/components/ui";

type Step = "password" | "2fa" | "reactivate";
type LoginResponse = { next?: "2fa" | "reactivate"; graceEndsAt?: string; user?: { status: string } };

/** RF01 · login em etapas: senha → 2FA (se houver) → oferta de reativação (se PENDING_DELETION). */
export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("password");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [grace, setGrace] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  function proceed(r: LoginResponse) {
    if (r.next === "2fa") setStep("2fa");
    else if (r.next === "reactivate") {
      setGrace(r.graceEndsAt ?? "");
      setStep("reactivate");
    } else {
      router.replace(r.user?.status === "PENDING_VERIFICATION" ? "/verificar-email" : "/feed");
      router.refresh();
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (step === "password") proceed(await api.post<LoginResponse>("/api/v1/auth/login", { email, password }));
      else if (step === "2fa") proceed(await api.post<LoginResponse>("/api/v1/auth/login/2fa", { code }));
      else proceed(await api.post<LoginResponse>("/api/v1/auth/reactivate"));
    } catch (err) {
      setError(err);
      if (err instanceof ApiError && err.status === 401 && step === "2fa") setCode("");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <h1 className="text-center">Entrar</h1>
      {step === "password" && (
        <>
          <p className="lead text-center">Use o e-mail e a senha da sua conta profissional.</p>
          <Field id="email" label="E-mail">
            {(p) => <input {...p} type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />}
          </Field>
          <Field id="password" label="Senha">
            {(p) => <input {...p} type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />}
          </Field>
        </>
      )}
      {step === "2fa" && (
        <>
          <p className="lead text-center">Digite o código do seu aplicativo autenticador ou um código de recuperação.</p>
          <Field id="code" label="Código" help="6 dígitos do app, ou um código de recuperação de uso único.">
            {(p) => <input {...p} type="text" inputMode="numeric" autoComplete="one-time-code" required value={code} onChange={(e) => setCode(e.target.value)} />}
          </Field>
        </>
      )}
      {step === "reactivate" && (
        <Notice tone="warning">
          Sua conta está em carência de exclusão{grace && grace !== "null" ? ` até ${new Date(grace).toLocaleDateString("pt-BR")}` : ""}. Quer reativá-la? Nada foi apagado ainda.
        </Notice>
      )}
      <div className="mt-4">
        <ProblemAlert error={error} />
      </div>
      <div className="toolbar">
        <Button type="submit" variant="primary" loading={busy} className="w-full">
          {step === "password" ? "Entrar" : step === "2fa" ? "Confirmar código" : "Reativar minha conta"}
        </Button>
        {step === "reactivate" && (
          <Link className="btn w-full" href="/login" onClick={() => setStep("password")}>
            Não, manter a exclusão
          </Link>
        )}
      </div>
      {step === "password" && (
        <p className="text-center text-sm">
          Ainda não tem conta? <Link href="/cadastro">Cadastre-se</Link>
        </p>
      )}
    </form>
  );
}
