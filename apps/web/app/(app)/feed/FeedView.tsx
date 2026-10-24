"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { startTransition, useOptimistic, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { useAsyncEffect } from "@/lib/use-async";
import { KINDS, ORIGINS, SPECIALTIES, STATES } from "@/lib/domain";
import { EMPTY_FEED, feedQuery, feedSearch, parseFeedParams, toggleSpecialty, type FeedParams } from "@/lib/feed-params";
import { PostCard, type Post } from "@/components/PostCard";
import { Loading, Notice, ProblemAlert } from "@/components/ui";

type Feed = { items: Post[]; count: number };

/** RF04 · feed segmentado por tipo, especialidade, origem e estado + "só sem resposta". O estado vive na URL. */
export function FeedView() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  // valor otimista: o controle reage na hora e a URL confirma em seguida
  const [params, setOptimistic] = useOptimistic(parseFeedParams(sp));
  const [feed, setFeed] = useState<Feed | null>(null);
  const [loadedFor, setLoadedFor] = useState<string | null>(null); // filtros da última resposta
  const [error, setError] = useState<unknown>(null);
  const loading = loadedFor !== sp.toString();

  useAsyncEffect(
    async () => {
      const f = await api.get<Feed>("/api/v1/feed", { query: feedQuery(parseFeedParams(sp)) });
      setError(null);
      setFeed(f);
      setLoadedFor(sp.toString());
    },
    setError,
    [sp],
  );

  const apply = (p: FeedParams) =>
    startTransition(() => {
      setOptimistic(p);
      router.replace(`${pathname}${feedSearch(p)}`, { scroll: false });
    });
  const noSeal = error instanceof ApiError && error.status === 403;

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1>Início</h1>
          <p className="lead">Casos, dúvidas e conteúdo da sua rede verificada, por ordem de chegada. Sem ranking por engajamento.</p>
        </div>
        <Link className="btn primary" href="/casos/novo">
          Publicar caso
        </Link>
      </div>

      {noSeal ? (
        <Notice tone="warning">
          O feed é só para contas com CRM verificado. <Link href="/perfil">Envie seu CRM e RQE no perfil</Link> para participar.
        </Notice>
      ) : (
        <>
          <div className="segments">
            <div className="tabs" role="tablist" aria-label="Tipo de publicação">
              {[["", "Tudo"], ...Object.entries(KINDS)].map(([code, label]) => (
                <button key={code} role="tab" type="button" aria-selected={params.kind === code} onClick={() => apply({ ...params, kind: code })}>
                  {label}
                </button>
              ))}
            </div>
            <div className="chips" role="group" aria-label="Especialidade">
              <span className="label">Especialidade</span>
              <button type="button" className="chip" aria-pressed={params.specialty.length === 0} onClick={() => apply({ ...params, specialty: [] })}>
                Todas
              </button>
              {Object.entries(SPECIALTIES).map(([code, name]) => (
                <button key={code} type="button" className="chip" aria-pressed={params.specialty.includes(code)} onClick={() => apply(toggleSpecialty(params, code))}>
                  {name}
                </button>
              ))}
            </div>
            <div className="row">
              <div className="chips" role="group" aria-label="Origem">
                <span className="label">Origem</span>
                <button type="button" className="chip" aria-pressed={params.origin === ""} onClick={() => apply({ ...params, origin: "" })}>
                  Todas
                </button>
                {Object.entries(ORIGINS).map(([code, name]) => (
                  <button key={code} type="button" className="chip" aria-pressed={params.origin === code} onClick={() => apply({ ...params, origin: code })}>
                    {name}
                  </button>
                ))}
              </div>
              <div className="chips" role="group" aria-label="Estado">
                <span className="label">Estado</span>
                <button type="button" className="chip" aria-pressed={params.state === ""} onClick={() => apply({ ...params, state: "" })}>
                  Todos
                </button>
                {Object.entries(STATES).map(([code, name]) => (
                  <button key={code} type="button" className="chip" aria-pressed={params.state === code} onClick={() => apply({ ...params, state: code })}>
                    {name}
                  </button>
                ))}
              </div>
              <label className="switch">
                <input type="checkbox" checked={params.unanswered} onChange={(e) => apply({ ...params, unanswered: e.target.checked })} />
                Só sem resposta
              </label>
            </div>
          </div>

          {error ? (
            <ProblemAlert error={error} />
          ) : !feed ? (
            <Loading what="Carregando o feed" />
          ) : (
            <>
              <p className="result-line" aria-live="polite">
                {loading ? "Atualizando…" : feed.count === 0 ? "Nenhuma publicação com esses filtros." : `${feed.count} ${feed.count === 1 ? "publicação" : "publicações"}`}
                {feedSearch(params) && (
                  <>
                    {" · "}
                    <button type="button" className="underline" onClick={() => apply(EMPTY_FEED)}>
                      limpar filtros
                    </button>
                  </>
                )}
              </p>
              <div className="feed" aria-busy={loading || undefined} style={loading ? { opacity: 0.6 } : undefined}>
                {feed.items.map((p) => (
                  <PostCard key={p.id} post={p} />
                ))}
                {feed.items.length === 0 && (
                  <div className="card empty">
                    Nada por aqui ainda. Entre em um <Link href="/grupos">grupo da sua especialidade</Link> ou <Link href="/casos/novo">publique o primeiro caso</Link>.
                  </div>
                )}
              </div>
            </>
          )}
        </>
      )}
    </>
  );
}
