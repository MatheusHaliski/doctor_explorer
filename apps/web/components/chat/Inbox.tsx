"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import type { ChatDevice } from "@doctor-explorer/crypto";
import { api, ApiError } from "@/lib/api";
import { conversationName, RETENTIONS, type ConversationSummary } from "@/lib/chat-api";
import { formatDateTime } from "@/lib/domain";
import { useAsyncEffect } from "@/lib/use-async";
import { useSession } from "@/components/session-context";
import { Button, Field, Loading, ProblemAlert } from "@/components/ui";

type Contact = { id: string; name: string; uf: string | null };

/** RF06 · conversas do médico e início de conversa 1:1 ou em grupo com colegas verificados. */
export function Inbox({ device }: { device: ChatDevice }) {
  const session = useSession();
  const router = useRouter();
  const [items, setItems] = useState<ConversationSummary[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [creating, setCreating] = useState(false);

  async function load() {
    await device.acceptWelcomes();
    setItems((await api.get<{ items: ConversationSummary[] }>("/api/v1/messaging/conversations")).items);
  }

  useAsyncEffect(load, setError, [device]);
  useEffect(() => {
    const t = setInterval(() => void load().catch(() => {}), 10_000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [device]);

  return (
    <div className="max-w-3xl">
      <div className="toolbar">
        <h1 className="mr-auto">Mensagens</h1>
        <Link className="btn" href="/mensagens/aparelhos">
          Aparelhos
        </Link>
        <Button type="button" variant="primary" onClick={() => setCreating((v) => !v)} aria-expanded={creating}>
          Nova conversa
        </Button>
      </div>
      <p className="lead">
        {/* eslint-disable-next-line @next/next/no-img-element -- ícone SVG local do design system */}
        <img src="/icons/topbar/e2ee.svg" alt="" className="inline-icon" /> Criptografadas ponta a ponta. O chat não é prontuário: o que for do paciente vai para o{" "}
        <Link href="/prontuario">prontuário</Link>.
      </p>
      {creating && (
        <NewConversation
          onCreate={async (input) => {
            const c = await device.createConversation(input);
            router.push(`/mensagens/${c.id}`);
          }}
          onExisting={(id) => router.push(`/mensagens/${id}`)}
        />
      )}
      <ProblemAlert error={error} />
      {!items ? (
        !error && <Loading what="Carregando conversas" />
      ) : items.length === 0 ? (
        <div className="card empty">Nenhuma conversa ainda.</div>
      ) : (
        <ul className="feed" aria-label="Conversas">
          {items.map((c) => (
            <li key={c.id} className={`card chat-row${c.unread ? " unread" : ""}`}>
              <Link href={`/mensagens/${c.id}`}>
                <strong>{conversationName(c, session.userId)}</strong>
                {c.unread && <span className="badge">nova</span>}
              </Link>
              <div className="text-sm text-text-2">
                {c.kind === "GROUP" ? `${c.members.length} membros · ` : ""}
                {c.lastMessageAt ? `última mensagem ${formatDateTime(c.lastMessageAt)}` : "sem mensagens"} · apaga em {RETENTIONS[c.retentionDays]}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function NewConversation({
  onCreate,
  onExisting,
}: {
  onCreate: (i: { kind: "DIRECT" | "GROUP"; memberIds: string[]; title?: string; retentionDays: number }) => Promise<void>;
  onExisting: (id: string) => void;
}) {
  const [q, setQ] = useState("");
  const [found, setFound] = useState<Contact[]>([]);
  const [picked, setPicked] = useState<Contact[]>([]);
  const [title, setTitle] = useState("");
  const [retention, setRetention] = useState(90);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const group = picked.length > 1;

  useAsyncEffect(
    async () => {
      if (q.trim().length < 2) return setFound([]);
      await new Promise((r) => setTimeout(r, 200));
      setFound((await api.get<{ items: Contact[] }>("/api/v1/messaging/contacts", { query: { q: q.trim() } })).items);
    },
    () => setFound([]),
    [q],
  );

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onCreate({ kind: group ? "GROUP" : "DIRECT", memberIds: picked.map((p) => p.id), title: group ? title : undefined, retentionDays: retention });
    } catch (err) {
      const existing = err instanceof ApiError ? (err.problem as { conversationId?: string }).conversationId : undefined;
      if (existing) onExisting(existing);
      else setError(err);
      setBusy(false);
    }
  }

  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);
  return (
    <form className="card mt-4" onSubmit={submit} noValidate aria-labelledby="new-title">
      <h2 id="new-title" className="mt-0 text-md">
        Nova conversa
      </h2>
      {picked.length > 0 && (
        <ul className="chips" aria-label="Participantes">
          {picked.map((p) => (
            <li key={p.id}>
              <span>{p.name}</span>
              <button type="button" aria-label={`Remover ${p.name}`} onClick={() => setPicked(picked.filter((x) => x.id !== p.id))}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <Field id="contact-q" label="Colega com selo verificado" errors={errs("memberIds")}>
        {(p) => <input {...p} className="input" type="search" autoComplete="off" placeholder="Nome" value={q} onChange={(e) => setQ(e.target.value)} />}
      </Field>
      {found.length > 0 && (
        <ul className="cid-results" aria-label="Colegas encontrados">
          {found
            .filter((c) => !picked.some((p) => p.id === c.id))
            .map((c) => (
              <li key={c.id}>
                <Button
                  type="button"
                  size="sm"
                  data-user-id={c.id}
                  onClick={() => {
                    setPicked([...picked, c]);
                    setQ("");
                  }}
                >
                  {c.name}
                  {c.uf ? ` · CRM/${c.uf}` : ""}
                </Button>
              </li>
            ))}
        </ul>
      )}
      {group && (
        <Field id="group-title" label="Nome do grupo" errors={errs("title")} help="O nome do grupo não é cifrado: não use dado de paciente.">
          {(p) => <input {...p} className="input" maxLength={80} value={title} onChange={(e) => setTitle(e.target.value)} />}
        </Field>
      )}
      <Field id="retention" label="Mensagens se apagam em" errors={errs("retentionDays")}>
        {(p) => (
          <select {...p} value={retention} onChange={(e) => setRetention(Number(e.target.value))}>
            {Object.entries(RETENTIONS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        )}
      </Field>
      <ProblemAlert error={error} />
      <Button type="submit" variant="primary" loading={busy} disabled={picked.length === 0 || (group && title.trim().length < 3)}>
        {group ? "Criar grupo" : "Começar conversa"}
      </Button>
    </form>
  );
}
