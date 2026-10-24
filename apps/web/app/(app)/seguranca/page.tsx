"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { useAsyncEffect } from "@/lib/use-async";
import { formatDateTime } from "@/lib/domain";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Mfa = { enabled: boolean; recoveryCodesLeft: number };
type Session = { handle: string; createdAt: string; lastAccessedAt: string; userAgent: string; current: boolean };

/** RF01 · sudo (10 min), troca de senha, TOTP + códigos de recuperação, sessões por aparelho; RF10 · pedir exclusão. */
export default function SecurityPage() {
  const router = useRouter();
  const [mfa, setMfa] = useState<Mfa | null>(null);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [sudoUntil, setSudoUntil] = useState<number | null>(null); // fim do modo sudo, ou null
  const [sudo, setSudo] = useState({ password: "", code: "" });
  const [pwd, setPwd] = useState({ currentPassword: "", newPassword: "" });
  const [setup, setSetup] = useState<{ otpauthUri: string; secret: string } | null>(null);
  const [confirmCode, setConfirmCode] = useState("");
  const [codes, setCodes] = useState<string[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();
  const hasSudo = sudoUntil !== null;

  async function load() {
    const [m, s] = await Promise.all([api.get<Mfa>("/api/v1/me/mfa"), api.get<{ sessions: Session[] }>("/api/v1/me/sessions")]);
    setMfa(m);
    setSessions(s.sessions);
  }
  useAsyncEffect(load, setError, []);

  async function run(key: string, fn: () => Promise<void>) {
    setBusy(key);
    setError(null);
    try {
      await fn();
    } catch (err) {
      if (err instanceof ApiError && err.problem.type?.toString().endsWith("sudo-necessario")) setSudoUntil(null);
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  const elevate = (e: FormEvent) => {
    e.preventDefault();
    run("sudo", async () => {
      const r = await api.post<{ sudoUntilSeconds: number }>("/api/v1/me/sudo", sudo);
      const until = Date.now() + r.sudoUntilSeconds * 1000;
      setSudoUntil(until);
      setTimeout(() => setSudoUntil((u) => (u === until ? null : u)), r.sudoUntilSeconds * 1000);
      setSudo({ password: "", code: "" });
      toast.show("Confirmado. Ações sensíveis liberadas por 10 minutos.");
    });
  };

  const changePassword = (e: FormEvent) => {
    e.preventDefault();
    run("pwd", async () => {
      const r = await api.post<{ otherSessionsEnded: number }>("/api/v1/me/password", pwd);
      setPwd({ currentPassword: "", newPassword: "" });
      toast.show(`Senha trocada. ${r.otherSessionsEnded} outra(s) sessão(ões) encerrada(s).`);
      await load();
    });
  };

  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);

  if (!mfa) return error ? <ProblemAlert error={error} /> : <Loading />;

  return (
    <div className="max-w-3xl">
      <h1>Sessões e segurança</h1>
      <p className="lead">Sessão opaca de 60 min (deslizante) e 24 h no máximo. Ações sensíveis pedem confirmação de senha e código.</p>
      <ProblemAlert error={error} />

      <h2>Confirmar identidade</h2>
      <form onSubmit={elevate} noValidate className="card">
        {hasSudo ? (
          <Notice tone="success">Identidade confirmada até {new Date(sudoUntil!).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}.</Notice>
        ) : (
          <>
            <p className="m-0 text-sm text-text-2">Necessário para trocar a senha, mexer no 2FA e pedir exclusão.</p>
            <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
              <Field id="sudo-password" label="Senha atual">
                {(p) => <input {...p} type="password" autoComplete="current-password" value={sudo.password} onChange={(e) => setSudo({ ...sudo, password: e.target.value })} />}
              </Field>
              <Field id="sudo-code" label={mfa.enabled ? "Código do autenticador" : "Código (só com 2FA)"}>
                {(p) => <input {...p} type="text" inputMode="numeric" autoComplete="one-time-code" value={sudo.code} disabled={!mfa.enabled} onChange={(e) => setSudo({ ...sudo, code: e.target.value })} />}
              </Field>
            </div>
            <div className="toolbar">
              <Button type="submit" variant="primary" loading={busy === "sudo"}>
                Confirmar
              </Button>
            </div>
          </>
        )}
      </form>

      <h2>Verificação em duas etapas (TOTP)</h2>
      <div className="card">
        {mfa.enabled ? (
          <>
            <Notice tone="success">2FA ativo · {mfa.recoveryCodesLeft} código(s) de recuperação restante(s).</Notice>
            <div className="toolbar">
              <Button type="button" disabled={!hasSudo} loading={busy === "regen"} onClick={() => run("regen", async () => setCodes((await api.post<{ recoveryCodes: string[] }>("/api/v1/me/mfa/recovery-codes")).recoveryCodes))}>
                Gerar novos códigos de recuperação
              </Button>
              <Button type="button" variant="danger" disabled={!hasSudo} loading={busy === "disable"} onClick={() => run("disable", async () => { await api.delete("/api/v1/me/mfa"); setCodes(null); toast.show("2FA desativado."); await load(); })}>
                Desativar 2FA
              </Button>
            </div>
          </>
        ) : setup ? (
          <>
            <p className="text-sm text-text-2">Adicione a conta no seu aplicativo autenticador e digite o código gerado.</p>
            <p className="m-0 text-sm">
              <a href={setup.otpauthUri}>Abrir no aplicativo autenticador</a> · ou digite a chave: <code className="font-mono">{setup.secret}</code>
            </p>
            <form onSubmit={(e) => { e.preventDefault(); run("confirm", async () => { const r = await api.post<{ recoveryCodes: string[] }>("/api/v1/me/mfa/totp/confirm", { code: confirmCode }); setCodes(r.recoveryCodes); setSetup(null); setConfirmCode(""); await load(); }); }} noValidate>
              <Field id="confirm-code" label="Código de 6 dígitos">
                {(p) => <input {...p} type="text" inputMode="numeric" autoComplete="one-time-code" value={confirmCode} onChange={(e) => setConfirmCode(e.target.value)} />}
              </Field>
              <div className="toolbar">
                <Button type="submit" variant="primary" loading={busy === "confirm"}>
                  Ativar 2FA
                </Button>
                <Button type="button" onClick={() => setSetup(null)}>
                  Cancelar
                </Button>
              </div>
            </form>
          </>
        ) : (
          <>
            <p className="m-0 text-sm text-text-2">Moderadores, administradores e gestores só usam a conta com 2FA ativo. Recomendado para todos.</p>
            <div className="toolbar">
              <Button type="button" variant="primary" disabled={!hasSudo} loading={busy === "setup"} onClick={() => run("setup", async () => setSetup(await api.post("/api/v1/me/mfa/totp/setup")))}>
                Ativar 2FA
              </Button>
            </div>
          </>
        )}
        {codes && (
          <div className="mt-3">
            <Notice tone="warning">Guarde estes códigos agora: eles aparecem uma única vez. Cada um vale para um login sem o aplicativo.</Notice>
            <div className="codes mt-2">
              {codes.map((c) => (
                <span key={c}>{c}</span>
              ))}
            </div>
          </div>
        )}
      </div>

      <h2>Trocar senha</h2>
      <form onSubmit={changePassword} noValidate className="card">
        <Field id="currentPassword" label="Senha atual" errors={errs("currentPassword")}>
          {(p) => <input {...p} type="password" autoComplete="current-password" value={pwd.currentPassword} disabled={!hasSudo} onChange={(e) => setPwd({ ...pwd, currentPassword: e.target.value })} />}
        </Field>
        <Field id="newPassword" label="Nova senha" errors={errs("newPassword")} help="De 12 a 128 caracteres. As outras sessões são encerradas.">
          {(p) => <input {...p} type="password" autoComplete="new-password" value={pwd.newPassword} disabled={!hasSudo} onChange={(e) => setPwd({ ...pwd, newPassword: e.target.value })} />}
        </Field>
        <div className="toolbar">
          <Button type="submit" variant="primary" disabled={!hasSudo} loading={busy === "pwd"}>
            Trocar senha
          </Button>
        </div>
      </form>

      <h2>Sessões ativas</h2>
      <div className="card list">
        {sessions.map((s) => (
          <div key={s.handle} className="item">
            <div className="text">
              <span className="font-bold">
                {s.userAgent || "Aparelho desconhecido"}
                {s.current && " · este aparelho"}
              </span>
              <small>
                Entrou em {formatDateTime(s.createdAt)} · última atividade {formatDateTime(s.lastAccessedAt)}
              </small>
            </div>
            {!s.current && (
              <Button size="sm" loading={busy === s.handle} onClick={() => run(s.handle, async () => { await api.delete(`/api/v1/me/sessions/${s.handle}`); toast.show("Sessão encerrada."); await load(); })}>
                Encerrar
              </Button>
            )}
          </div>
        ))}
        {sessions.length > 1 && (
          <div className="toolbar">
            <Button type="button" loading={busy === "others"} onClick={() => run("others", async () => { const r = await api.delete<{ ended: number }>("/api/v1/me/sessions"); toast.show(`${r.ended} sessão(ões) encerrada(s).`); await load(); })}>
              Encerrar todas as outras
            </Button>
          </div>
        )}
      </div>

      <h2 id="exclusao">Excluir conta</h2>
      <div className="card">
        <p className="m-0 text-sm text-text-2">Carência de 30 dias: entre de novo até lá para reativar. Registros clínicos ficam guardados por 20 anos por obrigação legal.</p>
        <div className="toolbar">
          <Button type="button" variant="danger" disabled={!hasSudo} loading={busy === "del"} onClick={() => { if (!confirm("Pedir a exclusão da conta? Você terá 30 dias para desistir.")) return; run("del", async () => { await api.post("/api/v1/me/deletion"); router.replace("/login"); router.refresh(); }); }}>
            Pedir exclusão da conta
          </Button>
        </div>
      </div>
      {toast.el}
    </div>
  );
}
