import Link from "next/link";
import { KINDS, ORIGINS, SPECIALTIES, STATES, initials, timeAgo } from "@/lib/domain";

export type Post = {
  id: string;
  kind: string;
  specialty: string;
  origin: string;
  state: string;
  title: string;
  summary: string;
  tags: string[];
  comments: number;
  mediaCount?: number;
  opinions: number;
  authorId: string;
  authorName: string;
  createdAt: string;
};

/** Card do feed: autor, eixos de classificação, título, resumo, estado e contadores. */
export function PostCard({ post }: { post: Post }) {
  const inst = post.origin === "instituicao";
  return (
    <article className="card post" aria-labelledby={`p-${post.id}`}>
      <div className={`avatar ${inst ? "inst" : ""}`} aria-hidden="true">
        {initials(post.authorName)}
      </div>
      <div className="min-w-0">
        <div className="head">
          <strong>{post.authorName}</strong>
          <span className="kind">{KINDS[post.kind] ?? post.kind}</span>
          <span>{SPECIALTIES[post.specialty] ?? post.specialty}</span>
          <span>{ORIGINS[post.origin] ?? post.origin}</span>
          <span>há {timeAgo(post.createdAt)}</span>
        </div>
        <h3 id={`p-${post.id}`}>
          <Link href={`/casos/${post.id}`}>{post.title}</Link>
        </h3>
        {post.summary && <p>{post.summary}</p>}
        <div className="foot">
          <span className={`status ${post.state}`}>{STATES[post.state] ?? post.state}</span>
          <span className="text-sm text-text-3">
            {post.opinions} {post.opinions === 1 ? "parecer" : "pareceres"} · {post.comments} {post.comments === 1 ? "comentário" : "comentários"}
            {post.mediaCount ? ` · ${post.mediaCount} ${post.mediaCount === 1 ? "imagem" : "imagens"}` : ""}
          </span>
          <span className="spacer" />
          {post.tags.length > 0 && (
            <div className="tags">
              {post.tags.map((t) => (
                <span key={t}>#{t}</span>
              ))}
            </div>
          )}
        </div>
      </div>
    </article>
  );
}
