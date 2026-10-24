"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api";
import { useAsyncEffect } from "@/lib/use-async";
import { formatDateTime } from "@/lib/domain";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Consent = { purpose: string; title: string; description: string; legalBasis: string; granted: boolean; changedAt: string };
type Operator = { nome: string; finalidade: string; dados: string };
type Ticket = { id: string; protocol: string; type: string; state: string; openedAt: string; dueAt: string; answer: string };
type Export = { id: string; state: string; expiresAt: string; downloadUrl: string };
const TICKET_TYPES: Record<string, string> = { ACCESS: "Acesso aos dados", CORRECTION: "Correção", DELETION: "Eliminação", PORTABILITY: "Portabilidade", REVOCATION: "Revogar consentimento", COMPLAINT: "Reclamação", OTHER: "Outro" };

/** RF10 · direitos do titular: consentimentos por finalidade, seus dados, exportação, compartilhamento, Encarregado. */
export default function PrivacyPage() {
  const [consents, setConsents] = useState<Consent[] | null>(null);
  const [operators, setOperators] = useState<Operator[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [data, setData] = useState<unknown>(null);
  const [exp, setExp] = useState<Export | null>(null);
  const [ticket, setTicket] = useState({ type: "ACCESS", message: "" });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();

  async function load() {
    const [c, s, t] = await Promise.all([
      api.get<{ consents: Consent[] }>("/api/v1/privacy/consents"),
      api.get<{ operators: Operator[] }>("/api/v1/privacy/sharing"),
      api.get<{ tickets: Ticket[] }>("/api/v1/privacy/dpo-requests"),
    ]);
    setConsents(c.consents);
    setOperators(s.operators);
    setTickets(t.tickets);
  }
  useAsyncEffect(load, setError, []);

  async function toggle(c: Consent, granted: boolean) {
    setConsents((cs) => cs!.map((x) => (x.purpose === c.purpose ? { ...x, granted } : x)));
    try {
      await api.put(`/api/v1/privacy/consents/${c.purpose}`, { granted });
      toast.show(`${c.title}: ${granted ? "ligado" : "desligado"}.`, () => toggle(c, !granted));
    } catch (err) {
      setConsents((cs) => cs!.map((x) => (x.purpose === c.purpose ? { ...x, granted: !granted } : x)));
      setError(err);
    }
  }

  async function requestExport() {
    setBusy("export");
    setError(null);
    try {
      setExp(await api.post<Export>("/api/v1/privacy/export"));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function openTicket(e: FormEvent) {
    e.preventDefault();
    setBusy("ticket");
    setError(null);
    try {
      const t = await api.post<Ticket>("/api/v1/privacy/dpo-requests", ticket);
      toast.show(`Pedido ${t.protocol} aberto. Resposta até ${formatDateTime(t.dueAt)}.`);
      setTicket({ type: "ACCESS", message: "" });
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  if (!consents) return error ? <ProblemAlert error={error} /> : <Loading />;

  return (
    <div className="max-w-3xl">
      <h1>Privacidade e seus dados</h1>
      <p className="lead">Tudo desligado por padrão. Dado clínico não depende de consentimento (LGPD Art. 11, II, “f”) e nunca aparece em log, URL ou notificação.</p>
      <ProblemAlert error={error} />

      <h2>Consentimentos por finalidade</h2>
      <div className="card list">
        {consents.map((c) => (
          <div key={c.purpose} className="item">
            <div className="text">
              <label htmlFor={`c-${c.purpose}`} className="font-bold">
                {c.title}
              </label>
              <small>
                {c.description}
                {c.changedAt && ` · alterado em ${formatDateTime(c.changedAt)}`}
              </small>
            </div>
            <span className="base">{c.legalBasis}</span>
            <label className="switch">
              <input id={`c-${c.purpose}`} type="checkbox" checked={c.granted} onChange={(e) => toggle(c, e.target.checked)} />
              <span className="sr-only">{c.title}</span>
            </label>
          </div>
        ))}
      </div>

      <h2>Seus dados</h2>
      <div className="card">
        <p className="m-0 text-text-2">Veja tudo que guardamos sobre você ou baixe uma cópia em JSON versionado (disponível por 24 h).</p>
        <div className="toolbar">
          <Button type="button" onClick={() => api.get("/api/v1/privacy/data").then(setData).catch(setError)}>
            {data ? "Atualizar" : "Ver meus dados"}
          </Button>
          <Button type="button" variant="primary" loading={busy === "export"} onClick={requestExport}>
            Pedir cópia dos dados
          </Button>
          {exp && (
            <a className="btn" href={exp.downloadUrl} download>
              Baixar cópia (até {formatDateTime(exp.expiresAt)})
            </a>
          )}
        </div>
        {data !== null && <pre className="json">{JSON.stringify(data, null, 2)}</pre>}
      </div>

      <h2>Com quem compartilhamos</h2>
      <div className="card list">
        {operators.map((o) => (
          <div key={o.nome} className="item">
            <div className="text">
              <span className="font-bold">{o.nome}</span>
              <small>
                {o.finalidade} · dados: {o.dados}
              </small>
            </div>
          </div>
        ))}
        <p className="m-0 pt-2 text-xs text-text-3">Base: Art. 18, VII. Nenhum operador recebe dado clínico identificado.</p>
      </div>

      <h2>Falar com o Encarregado</h2>
      <form onSubmit={openTicket} noValidate className="card">
        <Field id="ticket-type" label="Tipo do pedido">
          {(p) => (
            <select {...p} value={ticket.type} onChange={(e) => setTicket({ ...ticket, type: e.target.value })}>
              {Object.entries(TICKET_TYPES).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field id="ticket-message" label="Mensagem" help="Você recebe um protocolo e a data-limite da resposta.">
          {(p) => <textarea {...p} value={ticket.message} onChange={(e) => setTicket({ ...ticket, message: e.target.value })} required />}
        </Field>
        <div className="toolbar">
          <Button type="submit" variant="primary" loading={busy === "ticket"} disabled={!ticket.message.trim()}>
            Abrir pedido
          </Button>
        </div>
        {tickets.length > 0 && (
          <ul className="list mt-3">
            {tickets.map((t) => (
              <li key={t.id} className="item">
                <span className="text">
                  <span className="font-bold">{t.protocol}</span> · {TICKET_TYPES[t.type] ?? t.type}
                  <small>
                    {t.state} · prazo {formatDateTime(t.dueAt)}
                    {t.answer && ` · resposta: ${t.answer}`}
                  </small>
                </span>
              </li>
            ))}
          </ul>
        )}
      </form>

      <h2>Excluir conta</h2>
      <Notice tone="info">
        A exclusão tem carência de 30 dias e exige confirmação de senha (e código, se tiver 2FA). Fica em <Link href="/seguranca#exclusao">Sessões e segurança</Link>.
      </Notice>
      {toast.el}
    </div>
  );
}
