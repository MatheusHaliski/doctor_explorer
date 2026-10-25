"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { formatDateTime } from "@/lib/domain";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";
import { WeeklyChart, type Cell, type Week } from "./WeeklyChart";

type Engagement = {
  periodDays: number; minCell: number; activeUsers: Cell; casesPublished: Cell; opinions: Cell;
  medianHoursToFirstOpinion: Cell; callsPerWeek: Cell; weekly: Week[];
};
type Quality = {
  seals: { pending: number; manualReview: number; avgHoursToVerify: number | null };
  privacy?: { openIncidents: number; openRequests: number; overdueRequests: number };
  usability: { source: string | null; preliminary?: boolean; openBySeverity?: Record<string, number>; releaseBlockers?: number; open?: Array<{ screen: string; heuristic: string; severity: number; decision: string }> };
  slo: { availability: number; p95Millis: number; requests: number; source: string };
};
type AuditEvent = { id: string; seq: number; scope: string; at: string; actorId: string; actorRoles: string; action: string; category: string; resourceType: string; resourceId: string; patientRef: string; outcome: string; justification: string; hash: string };
type AuditResult = { events: AuditEvent[]; total: number; page: number; size: number; chain: { intact: boolean; broken: Array<{ scope: string; seq: number; reason: string }>; scopesChecked: number; eventsChecked: number }; patientRevealed: boolean };
type Export = { id: string; state: string; filters: string; events: number | null; sha256: string; signature: string; createdAt: string; expiresAt: string };

export type DashboardScope = { kind: "platform" } | { kind: "institution"; id: string; name: string };

/** A aba vive na URL (?aba=auditoria): a navegação lateral leva direto a cada uma. */
const TABS = [
  ["engajamento", "Engajamento"],
  ["auditoria", "Auditoria"],
  ["qualidade", "Qualidade"],
] as const;
type Tab = (typeof TABS)[number][0];

const CATEGORIES: Record<string, string> = { auth: "Acesso", privacy: "Privacidade", moderation: "Moderação", institution: "Instituição", clinical: "Clínico", audit: "Auditoria", community: "Comunidade", media: "Mídia", chat: "Chat", call: "Chamada" };

function base(scope: DashboardScope) {
  return scope.kind === "platform" ? "/api/v1/admin" : `/api/v1/institutions/${scope.id}/dashboard`;
}

function cellText(c: Cell, suffix = "") {
  return c.suppressed || c.value === null ? "<10" : `${c.value.toLocaleString("pt-BR")}${suffix}`;
}

/** RF13 · painel (plataforma ou instituição): engajamento k-anônimo, auditoria encadeada e qualidade. */
export function DashboardView({ scope }: { scope: DashboardScope }) {
  const router = useRouter();
  const pathname = usePathname();
  const aba = useSearchParams().get("aba");
  const tab: Tab = TABS.find(([id]) => id === aba)?.[0] ?? "engajamento";
  const setTab = (id: Tab) => router.replace(id === "engajamento" ? pathname : `${pathname}?aba=${id}`, { scroll: false });
  return (
    <div>
      <h1>{scope.kind === "platform" ? "Painel da plataforma" : `Painel · ${scope.name}`}</h1>
      <p className="lead">
        {scope.kind === "platform" ? "Engajamento agregado, auditoria e qualidade de toda a plataforma." : "Engajamento, auditoria e qualidade desta instituição e das unidades abaixo dela."} Exige 2FA; cada consulta à auditoria também é auditada.
      </p>
      <div className="tabs" role="tablist" aria-label="Seções do painel">
        {TABS.map(([id, label]) => (
          <button key={id} role="tab" type="button" aria-selected={tab === id} aria-controls={`panel-${id}`} id={`tab-${id}`} onClick={() => setTab(id)}>
            {label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} className="mt-4">
        {tab === "engajamento" && <EngagementTab scope={scope} />}
        {tab === "auditoria" && <AuditTab scope={scope} />}
        {tab === "qualidade" && <QualityTab scope={scope} />}
      </div>
    </div>
  );
}

function MfaHint({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.problem.type?.toString().endsWith("2fa-necessario")) {
    return <Notice tone="warning">Painéis de gestão e auditoria exigem verificação em duas etapas. Ative em Sessões e segurança, saia e entre de novo.</Notice>;
  }
  return <ProblemAlert error={error} />;
}

function EngagementTab({ scope }: { scope: DashboardScope }) {
  const [days, setDays] = useState(28);
  const [data, setData] = useState<Engagement | null>(null);
  const [error, setError] = useState<unknown>(null);
  useAsyncEffect(
    async () => {
      setData(await api.get<Engagement>(`${base(scope)}${scope.kind === "platform" ? "/dashboard" : ""}/engagement`, { query: { days } }));
    },
    setError,
    [days],
  );
  return (
    <section aria-label="Engajamento">
      <div className="toolbar" role="group" aria-label="Período">
        {[7, 28, 90].map((d) => (
          <button key={d} type="button" className="chip" aria-pressed={days === d} onClick={() => setDays(d)}>
            Últimos {d} dias
          </button>
        ))}
      </div>
      {error ? <MfaHint error={error} /> : !data ? <Loading /> : (
        <>
          <div className="kpis">
            {(
              [
                ["Usuários ativos", data.activeUsers, ""],
                ["Casos publicados", data.casesPublished, ""],
                ["Pareceres", data.opinions, ""],
                ["Mediana até o 1º parecer", data.medianHoursToFirstOpinion, " h"],
                ["Chamadas por semana", data.callsPerWeek, ""],
              ] as Array<[string, Cell, string]>
            ).map(([label, cell, suffix]) => (
              <div key={label} className="kpi">
                <div className="k-label">{label}</div>
                <div className="k-value">{cellText(cell, suffix)}</div>
                {cell.suppressed && <div className="k-note">Menos de 10 pessoas: suprimido para não expor ninguém.</div>}
              </div>
            ))}
          </div>
          <h2>Casos e pareceres por semana</h2>
          <div className="card">
            <WeeklyChart weeks={data.weekly} />
          </div>
        </>
      )}
    </section>
  );
}

/** Últimos 7 dias (hoje incluso), calculado uma vez na montagem. */
function lastWeek() {
  const now = new Date();
  const from = new Date(now.getTime() - 6 * 86400000);
  return { from: from.toISOString().slice(0, 10), to: now.toISOString().slice(0, 10) };
}

function AuditTab({ scope }: { scope: DashboardScope }) {
  const [f, setF] = useState(() => ({ ...lastWeek(), action: "", actorId: "", resourceType: "", justification: "", revealPatient: false }));
  const [applied, setApplied] = useState(f);
  const [page, setPage] = useState(0);
  const [res, setRes] = useState<AuditResult | null>(null);
  const [exports, setExports] = useState<Export[]>([]);
  const [download, setDownload] = useState<Record<string, { csvUrl: string; signatureUrl: string; verify: string }>>({});
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();

  // exportações da mesma visão (plataforma ou esta instituição): o download confere papel e 2FA de novo
  const exportsPath = scope.kind === "platform" ? "/api/v1/admin/audit/exports" : `/api/v1/institutions/${scope.id}/dashboard/audit/exports`;
  const loadExports = useCallback(async () => setExports((await api.get<{ exports: Export[] }>(exportsPath)).exports), [exportsPath]);
  useAsyncEffect(
    async () => {
      setError(null);
      setRes(await api.get<AuditResult>(`${base(scope)}${scope.kind === "platform" ? "" : ""}/audit`, { query: { ...applied, page, size: 25 } }));
      await loadExports();
    },
    setError,
    [applied, page],
  );

  function search(e: FormEvent) {
    e.preventDefault();
    setPage(0);
    setApplied({ ...f });
  }

  async function exportCsv() {
    setBusy("export");
    setError(null);
    try {
      await api.post(exportsPath, { from: f.from, to: f.to, action: f.action || null, actorId: f.actorId || null, resourceType: f.resourceType || null, justification: f.justification, revealPatient: f.revealPatient });
      toast.show("Exportação pronta: CSV assinado disponível por 24 h.");
      await loadExports();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function getLinks(id: string) {
    setBusy(id);
    try {
      const d = await api.get<{ csvUrl: string; signatureUrl: string; verify: string }>(`${exportsPath}/${id}/download`);
      setDownload({ ...download, [id]: d });
      await loadExports();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  const errs = (k: string) => (error instanceof ApiError ? error.field(k) : []);

  return (
    <section aria-label="Auditoria">
      <form onSubmit={search} noValidate className="card">
        <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))" }}>
          <Field id="from" label="De" errors={errs("from")}>{(p) => <input {...p} type="date" value={f.from} onChange={(e) => setF({ ...f, from: e.target.value })} />}</Field>
          <Field id="to" label="Até" errors={errs("to")} help="Até 31 dias por consulta.">{(p) => <input {...p} type="date" value={f.to} onChange={(e) => setF({ ...f, to: e.target.value })} />}</Field>
          <Field id="action" label="Ação começa com">
            {(p) => (
              <select {...p} value={f.action} onChange={(e) => setF({ ...f, action: e.target.value })}>
                <option value="">Todas</option>
                {Object.entries(CATEGORIES).map(([k, v]) => (
                  <option key={k} value={`${k}.`}>{v}</option>
                ))}
              </select>
            )}
          </Field>
          <Field id="actorId" label="Id de quem agiu">{(p) => <input {...p} type="text" value={f.actorId} onChange={(e) => setF({ ...f, actorId: e.target.value.trim() })} />}</Field>
          <Field id="resourceType" label="Tipo de recurso">{(p) => <input {...p} type="text" placeholder="ex.: user_account" value={f.resourceType} onChange={(e) => setF({ ...f, resourceType: e.target.value.trim() })} />}</Field>
        </div>
        <Field id="justification" label="Justificativa" errors={errs("justification")} help="Obrigatória para exportar e para ver o id do paciente (só auditor e Encarregado). Fica registrada.">
          {(p) => <textarea {...p} value={f.justification} onChange={(e) => setF({ ...f, justification: e.target.value })} />}
        </Field>
        <label className="check">
          <input type="checkbox" checked={f.revealPatient} onChange={(e) => setF({ ...f, revealPatient: e.target.checked })} />
          <span>Mostrar o id do paciente nos eventos clínicos</span>
        </label>
        <div className="toolbar">
          <Button type="submit" variant="primary">Filtrar</Button>
          <Button type="button" loading={busy === "export"} onClick={exportCsv}>Exportar CSV assinado</Button>
        </div>
      </form>
      <div className="mt-3">
        <MfaHint error={error} />
      </div>
      {!res ? <Loading /> : (
        <>
          <div className="toolbar">
            <span className={`pill ${res.chain.intact ? "chain-ok" : "chain-bad"}`} role="status">
              {res.chain.intact ? "✓ Cadeia íntegra" : "⚠ Cadeia com falha"} · {res.chain.eventsChecked} eventos conferidos
            </span>
            <span className="text-sm text-text-3">{res.total} eventos no filtro{res.patientRevealed && " · paciente visível (justificado)"}</span>
          </div>
          {!res.chain.intact && (
            <Notice tone="danger">
              {res.chain.broken.map((b) => `Escopo ${b.scope}, evento ${b.seq}: ${b.reason}.`).join(" ")} Avise o Encarregado e a segurança: isso indica alteração fora da aplicação.
            </Notice>
          )}
          <div className="card mt-3" style={{ overflowX: "auto" }}>
            <table className="data">
              <caption className="sr-only">Eventos de auditoria</caption>
              <thead>
                <tr><th>Quando</th><th>Ação</th><th>Quem</th><th>Recurso</th><th>Paciente</th><th>Resultado</th><th>Justificativa</th></tr>
              </thead>
              <tbody>
                {res.events.map((e) => (
                  <tr key={`${e.scope}-${e.seq}-${e.id}`}>
                    <td>{formatDateTime(e.at)}</td>
                    <td><span className="font-bold">{CATEGORIES[e.category] ?? e.category}</span><br /><span className="mono">{e.action}</span></td>
                    <td><span className="mono">{e.actorId ? e.actorId.slice(0, 8) : "sistema"}</span><br /><span className="text-xs text-text-3">{e.actorRoles}</span></td>
                    <td><span className="mono">{e.resourceType}</span><br /><span className="mono">{e.resourceId.slice(0, 40)}</span></td>
                    <td className="mono">{e.patientRef || "—"}</td>
                    <td>{e.outcome}</td>
                    <td className="text-xs">{e.justification || "—"}</td>
                  </tr>
                ))}
                {res.events.length === 0 && (
                  <tr><td colSpan={7} className="empty">Nenhum evento nesse filtro.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="toolbar">
            <Button size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</Button>
            <span className="text-sm text-text-3">Página {page + 1} de {Math.max(1, Math.ceil(res.total / res.size))}</span>
            <Button size="sm" disabled={(page + 1) * res.size >= res.total} onClick={() => setPage(page + 1)}>Próxima</Button>
          </div>
        </>
      )}

      <h2>Minhas exportações</h2>
      <div className="card list">
        {exports.length === 0 && <p className="m-0 text-sm text-text-3">Nenhuma exportação ainda.</p>}
        {exports.map((x) => (
          <div key={x.id} className="item" style={{ flexWrap: "wrap" }}>
            <div className="text">
              <span className="font-bold">{x.state}</span> · {x.events ?? 0} eventos · {formatDateTime(x.createdAt)}
              <small className="mono">sha256 {x.sha256.slice(0, 16)}… · {x.filters}</small>
              {download[x.id] && (
                <small>
                  <a href={download[x.id].csvUrl}>Baixar CSV</a> · <a href={download[x.id].signatureUrl}>Baixar assinatura</a> · <a href="/api/v1/audit/public-key">Chave pública</a> · links válidos por 15 min
                  <span className="mono block mt-1">{download[x.id].verify}</span>
                </small>
              )}
            </div>
            {(x.state === "PRONTA" || x.state === "BAIXADA") && (
              <Button size="sm" loading={busy === x.id} onClick={() => getLinks(x.id)}>Gerar links</Button>
            )}
          </div>
        ))}
      </div>
      {toast.el}
    </section>
  );
}

function QualityTab({ scope }: { scope: DashboardScope }) {
  const [q, setQ] = useState<Quality | null>(null);
  const [error, setError] = useState<unknown>(null);
  useAsyncEffect(async () => setQ(await api.get<Quality>(`${base(scope)}${scope.kind === "platform" ? "/dashboard" : ""}/quality`)), setError, []);
  if (error) return <MfaHint error={error} />;
  if (!q) return <Loading />;
  const sev = q.usability.openBySeverity ?? {};
  const maxSev = Math.max(1, ...Object.values(sev));
  const SEV_LABEL = ["0 · não é problema", "1 · cosmético", "2 · menor", "3 · maior", "4 · catástrofe"];
  return (
    <section aria-label="Qualidade">
      <div className="kpis">
        <div className="kpi"><div className="k-label">Selos pendentes</div><div className="k-value">{q.seals.pending}</div></div>
        <div className="kpi"><div className="k-label">Em revisão manual</div><div className="k-value">{q.seals.manualReview}</div></div>
        <div className="kpi"><div className="k-label">Tempo médio de verificação</div><div className="k-value">{q.seals.avgHoursToVerify === null ? "—" : `${q.seals.avgHoursToVerify.toLocaleString("pt-BR")} h`}</div><div className="k-note">últimos 90 dias</div></div>
        {q.privacy && (
          <>
            <div className="kpi"><div className="k-label">Incidentes de privacidade abertos</div><div className="k-value">{q.privacy.openIncidents}</div></div>
            <div className="kpi"><div className="k-label">Pedidos ao Encarregado em aberto</div><div className="k-value">{q.privacy.openRequests}</div><div className="k-note">{q.privacy.overdueRequests} fora do prazo</div></div>
          </>
        )}
        <div className="kpi"><div className="k-label">Disponibilidade</div><div className="k-value">{(q.slo.availability * 100).toLocaleString("pt-BR", { maximumFractionDigits: 2 })} %</div><div className="k-note">{q.slo.requests} requisições</div></div>
        <div className="kpi"><div className="k-label">Latência p95</div><div className="k-value">{q.slo.p95Millis.toLocaleString("pt-BR")} ms</div><div className="k-note">{q.slo.source}</div></div>
      </div>

      <h2>Usabilidade · problemas abertos por severidade</h2>
      {q.usability.source ? (
        <div className="card">
          {q.usability.preliminary && <Notice tone="info">Pré-avaliação da equipe de desenvolvimento. A avaliação formal com 3 a 5 avaliadores ainda não aconteceu.</Notice>}
          <p className="text-sm text-text-2">
            Fonte: <span className="mono">{q.usability.source}</span> · {q.usability.releaseBlockers ? <strong>{q.usability.releaseBlockers} bloqueiam o release (severidade 3 ou 4)</strong> : "nenhum problema bloqueia o release"}
          </p>
          <table className="data">
            <caption className="sr-only">Problemas abertos por severidade</caption>
            <thead><tr><th>Severidade</th><th className="num">Abertos</th><th aria-hidden="true" style={{ width: "50%" }} /></tr></thead>
            <tbody>
              {SEV_LABEL.map((label, i) => (
                <tr key={label}>
                  <td>{label}</td>
                  <td className="num">{sev[String(i)] ?? 0}</td>
                  <td aria-hidden="true">
                    <svg viewBox="0 0 200 12" width="100%" height="12" preserveAspectRatio="none">
                      {(sev[String(i)] ?? 0) > 0 && <rect x="0" y="2" height="8" rx="2" width={((sev[String(i)] ?? 0) / maxSev) * 200} fill="var(--series-1)" />}
                    </svg>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {q.usability.open && q.usability.open.length > 0 && (
            <ul className="list mt-3">
              {q.usability.open.map((o, i) => (
                <li key={i} className="item">
                  <span className="text">{o.screen} · {o.heuristic}<small>{o.decision}</small></span>
                  <span className="base">média {o.severity.toLocaleString("pt-BR")}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <Notice tone="warning">Nenhuma planilha de avaliação heurística encontrada.</Notice>
      )}
    </section>
  );
}
