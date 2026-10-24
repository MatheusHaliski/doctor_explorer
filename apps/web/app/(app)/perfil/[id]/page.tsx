import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { InviteToInstitution } from "@/components/InviteToInstitution";
import { Seal } from "@/components/Seal";
import { SPECIALTIES, VISIBILITIES, initials } from "@/lib/domain";
import { serverGet } from "@/lib/server-api";

type Profile = { id: string; displayName: string; seal: string; crm: string; specialties: string[]; bio?: string; visibility?: string; institutions?: Array<{ institutionId: string; role: string }> };

export const metadata: Metadata = { title: "Perfil" };

/** RF03 · perfil de um colega, com a visibilidade decidida pela API (404 quando não há acesso). */
export default async function PublicProfilePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { data: p } = await serverGet<Profile>(`/api/v1/profiles/${id}`);
  if (!p) notFound();
  const minimal = p.bio === undefined;
  return (
    <div className="max-w-3xl">
      <p className="result-line">
        <Link href="/feed">← Voltar ao início</Link>
      </p>
      <article className="card">
        <div className="flex flex-wrap items-center gap-4">
          <div className="avatar" aria-hidden="true" style={{ width: 64, height: 64, fontSize: "1.25rem" }}>
            {initials(p.displayName)}
          </div>
          <div>
            <h1 className="m-0">{p.displayName}</h1>
            <p className="m-0 text-text-2">
              {p.crm || "Sem CRM informado"}
              {p.specialties.length > 0 && ` · ${p.specialties.map((s) => SPECIALTIES[s] ?? s).join(", ")}`}
            </p>
          </div>
          <Seal status={p.seal} className="ml-auto" />
        </div>
        {minimal ? (
          <p className="mt-4 text-sm text-text-3">Perfil público mínimo. Entre com uma conta verificada para ver a apresentação completa.</p>
        ) : (
          <>
            {p.bio && <p className="mt-4 whitespace-pre-wrap">{p.bio}</p>}
            {p.institutions && p.institutions.length > 0 && (
              <ul className="list mt-2">
                {p.institutions.map((i) => (
                  <li key={i.institutionId} className="item">
                    <span className="text">
                      <Link href={`/instituicoes/${i.institutionId}`}>Instituição vinculada</Link>
                    </span>
                    <span className="base">{i.role}</span>
                  </li>
                ))}
              </ul>
            )}
            {p.visibility && <p className="mt-3 text-xs text-text-3">Visibilidade: {VISIBILITIES[p.visibility]?.label ?? p.visibility}</p>}
          </>
        )}
      </article>
      <InviteToInstitution userId={p.id} />
    </div>
  );
}
