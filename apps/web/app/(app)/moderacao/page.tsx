"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { SPECIALTIES, formatDateTime } from "@/lib/domain";
import { useAsyncEffect } from "@/lib/use-async";
import { isModerator, useSession } from "@/components/session-context";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Check = { source: string; checkedAt: string; outcome: string; reason: string };
type SealItem = { userId: string; userName: string; crmNumber: string; crmUf: string; seal: string; since: string; specialties: Array<{ specialtyCode: string; rqe: string; confirmed: boolean }>; checks: Check[] };
type GroupRequest = { userId: string; userName: string; specialtyCode: string; requestedAt: string };
type Queue = { seals: SealItem[]; groupRequests: GroupRequest[] };

/** RF02 (US06) + RF04 · fila do moderador. Rota protegida pela API: papel MODERATOR/ADMIN + 2FA ativo. */
export default function ModerationPage() {
  const session = useSession();
  const [queue, setQueue] = useState<Queue | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();

  const load = useCallback(async () => setQueue(await api.get<Queue>("/api/v1/admin/moderation/queue")), []);
  useAsyncEffect(load, setError, [load]);

  async function run(key: string, fn: () => Promise<string>) {
    setBusy(key);
    setError(null);
    try {
      toast.show(await fn());
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  if (!isModerator(session)) {
    return (
      <>
        <h1>Fila de moderação</h1>
        <Notice tone="warning">Só moderadores e administradores com 2FA ativo entram aqui.</Notice>
      </>
    );
  }
  if (error instanceof ApiError && error.status === 403) {
    return (
      <>
        <h1>Fila de moderação</h1>
        <Notice tone="warning">
          Seu papel exige verificação em duas etapas ativa. <Link href="/seguranca">Ative o 2FA</Link>, saia e entre de novo.
        </Notice>
      </>
    );
  }

  return (
    <div className="max-w-3xl">
      <h1>Fila de moderação</h1>
      <p className="lead">Selos sem resposta da fonte do CFM por 24 h (ou CRM já verificado em outra conta) e pedidos de entrada em grupo sem RQE. Toda decisão fica registrada com seu nome e o motivo.</p>
      <ProblemAlert error={error} />
      {!queue ? (
        <Loading />
      ) : (
        <>
          <h2>Selos em revisão manual ({queue.seals.length})</h2>
          {queue.seals.length === 0 && <div className="card empty">Nenhum selo aguardando.</div>}
          <div className="feed">
            {queue.seals.map((s) => (
              <article key={s.userId} className="card" aria-labelledby={`s-${s.userId}`}>
                <h3 id={`s-${s.userId}`} className="m-0 text-lg">
                  <Link href={`/perfil/${s.userId}`}>{s.userName}</Link> · CRM-{s.crmUf} {s.crmNumber}
                </h3>
                <p className="m-0 text-sm text-text-3">
                  Em revisão desde {formatDateTime(s.since)}
                  {s.specialties.length > 0 && ` · ${s.specialties.map((c) => `${SPECIALTIES[c.specialtyCode] ?? c.specialtyCode} (RQE ${c.rqe})`).join(", ")}`}
                </p>
                <details className="mt-2">
                  <summary className="cursor-pointer text-sm font-bold">Evidências ({s.checks.length})</summary>
                  <ul className="list">
                    {s.checks.map((c, i) => (
                      <li key={i} className="item">
                        <span className="text">
                          {c.outcome} <small>{c.reason || c.source}</small>
                        </span>
                        <span className="base">{formatDateTime(c.checkedAt)}</span>
                      </li>
                    ))}
                  </ul>
                </details>
                <Field id={`r-${s.userId}`} label="Motivo (obrigatório para recusar; o médico vai ler)" errors={busy === null && error instanceof ApiError ? error.field("reason") : []}>
                  {(p) => <textarea {...p} value={reasons[s.userId] ?? ""} onChange={(e) => setReasons({ ...reasons, [s.userId]: e.target.value })} />}
                </Field>
                <div className="toolbar">
                  <Button variant="primary" size="sm" loading={busy === `a-${s.userId}`} onClick={() => run(`a-${s.userId}`, async () => { await api.post(`/api/v1/admin/moderation/seals/${s.userId}/approve`, { reason: reasons[s.userId] ?? "" }); return `Selo de ${s.userName} aprovado.`; })}>
                    Aprovar selo
                  </Button>
                  <Button variant="danger" size="sm" loading={busy === `r-${s.userId}`} onClick={() => run(`r-${s.userId}`, async () => { await api.post(`/api/v1/admin/moderation/seals/${s.userId}/reject`, { reason: reasons[s.userId] ?? "" }); return `Selo de ${s.userName} recusado.`; })}>
                    Recusar
                  </Button>
                </div>
              </article>
            ))}
          </div>

          <h2>Pedidos de entrada em grupo ({queue.groupRequests.length})</h2>
          <div className="card list">
            {queue.groupRequests.length === 0 && <p className="m-0 text-sm text-text-3">Nenhum pedido pendente.</p>}
            {queue.groupRequests.map((g) => (
              <div key={`${g.userId}-${g.specialtyCode}`} className="item">
                <div className="text">
                  <Link href={`/perfil/${g.userId}`} className="font-bold">
                    {g.userName}
                  </Link>
                  <small>
                    quer entrar em {SPECIALTIES[g.specialtyCode] ?? g.specialtyCode} · pedido em {formatDateTime(g.requestedAt)}
                  </small>
                </div>
                <Button size="sm" variant="primary" loading={busy === `g-${g.userId}-${g.specialtyCode}`} onClick={() => run(`g-${g.userId}-${g.specialtyCode}`, async () => { await api.post(`/api/v1/admin/moderation/groups/${g.specialtyCode.toLowerCase()}/members/${g.userId}/approve`); return `${g.userName} entrou em ${SPECIALTIES[g.specialtyCode] ?? g.specialtyCode}.`; })}>
                  Aprovar entrada
                </Button>
              </div>
            ))}
          </div>
          <p className="mt-4 text-sm text-text-3">Para ocultar uma publicação, abra o caso: moderadores veem o botão “Ocultar” lá.</p>
        </>
      )}
      {toast.el}
    </div>
  );
}
