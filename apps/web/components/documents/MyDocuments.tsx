"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/domain";
import { DOC_STATES, KINDS, type DocState, type Kind } from "@/lib/documents";
import { useAsyncEffect } from "@/lib/use-async";
import { Loading, ProblemAlert } from "@/components/ui";

type Mine = { id: string; kind: Kind; state: DocState; patientId: string; patientName: string; items: number; signedAt: string | null; validUntil: string | null; createdAt: string };

/** RF09 · receitas e pedidos de exame do médico, de todos os pacientes; um novo documento nasce no paciente. */
export function MyDocuments() {
  const [items, setItems] = useState<Mine[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  useAsyncEffect(async () => setItems((await api.get<{ items: Mine[] }>("/api/v1/clinical/documents")).items), setError, []);

  return (
    <div className="max-w-3xl">
      <h1>Prescrições</h1>
      <p className="lead">
        Suas receitas e pedidos de exame, assinados com certificado ICP-Brasil. Para emitir um novo, abra o paciente no <Link href="/prontuario">prontuário</Link>. A farmácia
        confere em <Link href="/validar">/validar</Link>.
      </p>
      <ProblemAlert error={error} />
      {!items ? (
        !error && <Loading what="Carregando suas prescrições" />
      ) : items.length === 0 ? (
        <div className="card empty">Nenhuma receita ou pedido de exame ainda.</div>
      ) : (
        <ul className="feed" aria-label="Seus documentos">
          {items.map((d) => (
            <li key={d.id} className="card">
              <Link href={`/prontuario/documentos/${d.id}`}>
                <strong>{KINDS[d.kind]}</strong> · {d.patientName}
              </Link>
              <div className="text-sm text-text-2">
                {DOC_STATES[d.state]} · {formatDateTime(d.signedAt ?? d.createdAt)} · {d.items} {d.items === 1 ? "item" : "itens"}
                {d.validUntil ? ` · válida até ${formatDate(d.validUntil)}` : ""}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
