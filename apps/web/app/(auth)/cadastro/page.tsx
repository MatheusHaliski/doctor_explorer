"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { PRIVACY_VERSION, TERMS_VERSION } from "@/lib/domain";
import { Button, Field, ProblemAlert } from "@/components/ui";

/** RF01 · cadastro: todos os erros juntos (422), aceite com caixas desmarcadas, versão dos textos gravada. */
export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({ displayName: "", email: "", birthDate: "", password: "", acceptTerms: false, acceptPrivacy: false });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.post("/api/v1/auth/register", { ...form, termsVersion: TERMS_VERSION, privacyVersion: PRIVACY_VERSION });
      router.replace("/verificar-email");
      router.refresh();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <h1 className="text-center">Criar conta</h1>
      <p className="lead text-center">Só para profissionais de saúde maiores de 18 anos. O selo CRM vem depois, no perfil.</p>
      <Field id="displayName" label="Nome completo" errors={errs("displayName")} help="Igual ao registrado no CRM, para a verificação conferir.">
        {(p) => <input {...p} type="text" autoComplete="name" value={form.displayName} onChange={set("displayName")} />}
      </Field>
      <Field id="email" label="E-mail" errors={errs("email")}>
        {(p) => <input {...p} type="email" autoComplete="email" value={form.email} onChange={set("email")} />}
      </Field>
      <Field id="birthDate" label="Data de nascimento" errors={errs("birthDate")}>
        {(p) => <input {...p} type="date" autoComplete="bday" value={form.birthDate} onChange={set("birthDate")} />}
      </Field>
      <Field id="password" label="Senha" errors={errs("password")} help="De 12 a 128 caracteres. Uma frase longa vale mais que símbolos.">
        {(p) => <input {...p} type="password" autoComplete="new-password" value={form.password} onChange={set("password")} />}
      </Field>
      <div className="field">
        <label className="check" htmlFor="acceptTerms">
          <input id="acceptTerms" type="checkbox" checked={form.acceptTerms} onChange={set("acceptTerms")} aria-invalid={errs("acceptTerms").length > 0 || undefined} />
          <span>
            Li e aceito os <a href="/termos" target="_blank" rel="noopener">Termos de Uso</a> (versão {TERMS_VERSION}).
          </span>
        </label>
        {errs("acceptTerms").length > 0 && <span className="err" role="alert">{errs("acceptTerms").join(" ")}</span>}
        <label className="check" htmlFor="acceptPrivacy">
          <input id="acceptPrivacy" type="checkbox" checked={form.acceptPrivacy} onChange={set("acceptPrivacy")} aria-invalid={errs("acceptPrivacy").length > 0 || undefined} />
          <span>
            Li a <a href="/privacidade-politica" target="_blank" rel="noopener">Política de Privacidade</a> (versão {PRIVACY_VERSION}).
          </span>
        </label>
        {errs("acceptPrivacy").length > 0 && <span className="err" role="alert">{errs("acceptPrivacy").join(" ")}</span>}
      </div>
      <ProblemAlert error={error} />
      <div className="toolbar">
        <Button type="submit" variant="primary" loading={busy} className="w-full">
          Criar conta
        </Button>
      </div>
      <p className="text-center text-sm">
        Já tem conta? <Link href="/login">Entrar</Link>
      </p>
    </form>
  );
}
