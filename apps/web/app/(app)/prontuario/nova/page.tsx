"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import type { Composition, PatientView } from "@/lib/ehr";
import { PatientPicker } from "@/components/ehr/PatientPicker";
import { Notice, ProblemAlert } from "@/components/ui";

/**
 * RF08 critério 3 · "Registrar no prontuário" a partir de uma discussão: escolhe o paciente no cofre e abre a nota
 * com o vínculo pelo id opaco do post. O fórum nunca recebe nada do paciente.
 */
export default function NewFromDiscussion() {
  const router = useRouter();
  const post = useSearchParams().get("post") ?? "";
  const [error, setError] = useState<unknown>(null);

  async function pick(p: PatientView) {
    try {
      const c = await api.post<Composition>("/api/v1/clinical/compositions", {
        patientId: p.id,
        context: post ? "DISCUSSAO_DE_CASO" : "CONSULTA",
        links: post ? [{ type: "POST", ref: post }] : [],
      });
      router.push(`/prontuario/notas/${c.id}`);
    } catch (err) {
      setError(err);
    }
  }

  return (
    <div className="max-w-3xl">
      <h1>Registrar no prontuário</h1>
      {post ? (
        <Notice tone="info">
          A nota fica vinculada à <Link href={`/casos/${post}`}>discussão</Link> só pelo id dela. Quem lê o fórum não vê o paciente nem a nota.
        </Notice>
      ) : null}
      <p className="lead">Escolha o paciente no seu cofre clínico ou cadastre um novo.</p>
      <ProblemAlert error={error} />
      <PatientPicker actionLabel="Registrar" onPick={pick} />
    </div>
  );
}
