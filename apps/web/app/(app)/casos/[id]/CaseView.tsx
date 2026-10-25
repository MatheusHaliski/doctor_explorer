"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "@/lib/api";
import { KINDS, ORIGINS, SPECIALTIES, STATES, formatDateTime, initials } from "@/lib/domain";
import type { Post } from "@/components/PostCard";
import { isModerator, useSession } from "@/components/session-context";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Comment = { id: string; authorId: string; authorName: string; body: string; opinion: boolean; createdAt: string };
type Case = Post & {
  sections: { complaint: string; history: string; exam: string; workup: string; question: string };
  comments: Comment[];
  canRecordInEhr: boolean;
  media: Array<{ id: string; url: string; width: number; height: number }>;
};
const LABELS: Record<string, string> = { complaint: "Queixa e contexto", history: "História", exam: "Exame físico", workup: "Exames complementares", question: "Pergunta" };

/** RF04 · caso com pareceres; resolver só pelo autor; "Registrar no prontuário" só para o autor (RF08). */
export function CaseView({ id }: { id: string }) {
  const router = useRouter();
  const session = useSession();
  const [c, setC] = useState<Case | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [body, setBody] = useState("");
  const [opinion, setOpinion] = useState(true);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = useCallback(() => api.get<Case>(`/api/v1/posts/${id}`).then(setC).catch(setError), [id]);
  useEffect(() => {
    load();
  }, [load]);

  async function comment(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.post(`/api/v1/posts/${id}/comments`, { body, opinion });
      setBody("");
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function resolve() {
    try {
      await api.post(`/api/v1/posts/${id}/resolve`);
      toast.show("Caso marcado como resolvido.");
      await load();
    } catch (err) {
      setError(err);
    }
  }

  async function remove() {
    if (!confirm("Excluir esta publicação? Os pareceres dos colegas também somem do feed.")) return;
    try {
      await api.delete(`/api/v1/posts/${id}`);
      router.replace("/feed");
    } catch (err) {
      setError(err);
    }
  }

  async function hide() {
    const reason = prompt("Motivo da ocultação (fica registrado):", "moderação");
    if (reason === null) return;
    try {
      await api.post(`/api/v1/admin/posts/${id}/hide`, { reason });
      toast.show("Publicação ocultada.");
      router.replace("/feed");
    } catch (err) {
      setError(err);
    }
  }

  if (error && !c) return <ProblemAlert error={error} />;
  if (!c) return <Loading what="Carregando o caso" />;
  const author = c.canRecordInEhr;

  return (
    <div className="max-w-3xl">
      <p className="result-line">
        <Link href="/feed">← Voltar ao início</Link>
      </p>
      <article className="card">
        <div className="post">
          <div className="avatar" aria-hidden="true">
            {initials(c.authorName)}
          </div>
          <div className="min-w-0">
            <div className="head">
              <strong>
                <Link href={`/perfil/${c.authorId}`}>{c.authorName}</Link>
              </strong>
              <span className="kind">{KINDS[c.kind] ?? c.kind}</span>
              <span>{SPECIALTIES[c.specialty] ?? c.specialty}</span>
              <span>{ORIGINS[c.origin] ?? c.origin}</span>
              <span>{formatDateTime(c.createdAt)}</span>
            </div>
            <h1 className="mt-2">{c.title}</h1>
            <span className={`status ${c.state}`}>{STATES[c.state] ?? c.state}</span>
          </div>
        </div>
        {Object.entries(c.sections)
          .filter(([, v]) => v)
          .map(([k, v]) => (
            <section key={k} className="mt-4">
              <h2 className="mt-2 text-md">{LABELS[k] ?? k}</h2>
              <p className="whitespace-pre-wrap text-text-2">{v}</p>
            </section>
          ))}
        {c.media.length > 0 && (
          <div className="case-media" aria-label="Imagens do caso (anonimizadas)">
            {c.media.map((m) => (
              <a key={m.id} href={m.url} target="_blank" rel="noreferrer">
                {/* eslint-disable-next-line @next/next/no-img-element -- URL pré-assinada de 10 min, só para quem pode ver o caso */}
                <img src={m.url} width={m.width} height={m.height} alt="Imagem do caso, anonimizada e revisada pelo autor" loading="lazy" />
              </a>
            ))}
          </div>
        )}
        {c.tags.length > 0 && (
          <div className="tags mt-4">
            {c.tags.map((t) => (
              <span key={t}>#{t}</span>
            ))}
          </div>
        )}
        {isModerator(session) && !author && (
          <div className="toolbar mt-4">
            <Button type="button" variant="danger" onClick={hide}>
              Ocultar (moderação)
            </Button>
          </div>
        )}
        {author && (
          <div className="toolbar mt-4">
            {c.state !== "RESOLVIDO" && (
              <Button type="button" onClick={resolve}>
                Marcar como resolvido
              </Button>
            )}
            <Link className="btn" href={`/prontuario/nova?post=${c.id}`}>
              Registrar no prontuário
            </Link>
            <Button type="button" variant="danger" onClick={remove}>
              Excluir
            </Button>
          </div>
        )}
      </article>

      <h2>
        {c.opinions} {c.opinions === 1 ? "parecer" : "pareceres"} · {c.comments.length} {c.comments.length === 1 ? "comentário" : "comentários"}
      </h2>
      <div className="feed">
        {c.comments.map((m) => (
          <article key={m.id} className="card post">
            <div className="avatar" aria-hidden="true">
              {initials(m.authorName)}
            </div>
            <div className="min-w-0">
              <div className="head">
                <strong>
                  <Link href={`/perfil/${m.authorId}`}>{m.authorName}</Link>
                </strong>
                {m.opinion && <span className="kind">Parecer</span>}
                <span>{formatDateTime(m.createdAt)}</span>
              </div>
              <p className="mt-1 whitespace-pre-wrap">{m.body}</p>
            </div>
          </article>
        ))}
        {c.comments.length === 0 && <div className="card empty">Ninguém respondeu ainda. Seu parecer muda o estado para “Em discussão”.</div>}
      </div>

      {c.state === "RESOLVIDO" ? (
        <Notice tone="success">Caso resolvido pelo autor. Novos pareceres não são aceitos; comentários continuam abertos.</Notice>
      ) : null}
      <form onSubmit={comment} noValidate className="card mt-4">
        <Field id="body" label={opinion ? "Seu parecer" : "Seu comentário"} help="Sem dado de paciente aqui também. Um parecer é uma opinião clínica fundamentada; um comentário é conversa.">
          {(p) => <textarea {...p} value={body} onChange={(e) => setBody(e.target.value)} required />}
        </Field>
        <label className="switch">
          <input type="checkbox" checked={opinion} onChange={(e) => setOpinion(e.target.checked)} disabled={c.state === "RESOLVIDO"} />
          Registrar como parecer
        </label>
        <ProblemAlert error={error} />
        <div className="toolbar">
          <Button type="submit" variant="primary" loading={busy} disabled={!body.trim()}>
            Enviar
          </Button>
        </div>
      </form>
      {toast.el}
    </div>
  );
}
