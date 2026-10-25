"use client";

import { useState } from "react";
import { api } from "@/lib/api";
import { tarjaLabel, type CatalogExam, type CatalogMedication } from "@/lib/documents";
import { useAsyncEffect } from "@/lib/use-async";
import { Button } from "@/components/ui";

/** Busca no catálogo CMED/Anvisa (medicamentos) ou LOINC↔TUSS (exames). Controle especial aparece, mas não entra. */
export function MedicationSearch({ onPick, taken }: { onPick: (m: CatalogMedication) => void; taken: string[] }) {
  const [q, setQ] = useState("");
  const [items, setItems] = useState<CatalogMedication[]>([]);

  useAsyncEffect(
    async () => {
      if (q.trim().length < 3) return setItems([]);
      await new Promise((r) => setTimeout(r, 200));
      setItems((await api.get<{ items: CatalogMedication[] }>("/api/v1/clinical/catalog/medications", { query: { q: q.trim() } })).items);
    },
    () => setItems([]),
    [q],
  );

  return (
    <div>
      <label htmlFor="med-q">Adicionar medicamento</label>
      <input id="med-q" className="input" type="search" placeholder="Substância, produto ou apresentação (ex.: amoxicilina 500)" value={q} onChange={(e) => setQ(e.target.value)} autoComplete="off" />
      {items.length > 0 && (
        <ul className="cid-results" aria-label="Medicamentos encontrados">
          {items.map((m) => {
            const blocked = m.specialControl;
            const dup = taken.includes(m.ggrem);
            return (
              <li key={m.ggrem}>
                <Button
                  type="button"
                  size="sm"
                  disabled={blocked || dup}
                  onClick={() => {
                    onPick(m);
                    setQ("");
                  }}
                >
                  <span>
                    <strong>{m.substance}</strong> · {m.presentation} <span className="text-text-3">({m.product}, {m.laboratory})</span>
                    {tarjaLabel(m) && <span className="doc-flag"> {tarjaLabel(m)}</span>}
                    {dup && <span className="doc-flag"> já na receita</span>}
                  </span>
                </Button>
              </li>
            );
          })}
        </ul>
      )}
      {items.some((m) => m.specialControl) && (
        <p className="text-sm text-text-2">Medicamentos de controle especial (Portaria 344/1998) ficam fora desta versão: use o receituário próprio.</p>
      )}
    </div>
  );
}

export function ExamSearch({ onPick, taken }: { onPick: (e: CatalogExam) => void; taken: string[] }) {
  const [q, setQ] = useState("");
  const [items, setItems] = useState<CatalogExam[]>([]);

  useAsyncEffect(
    async () => {
      if (q.trim().length < 2) return setItems([]);
      await new Promise((r) => setTimeout(r, 200));
      setItems((await api.get<{ items: CatalogExam[] }>("/api/v1/clinical/catalog/exams", { query: { q: q.trim() } })).items.filter((e) => !taken.includes(e.loinc)));
    },
    () => setItems([]),
    [q, taken.join(",")],
  );

  return (
    <div>
      <label htmlFor="exam-q">Adicionar exame</label>
      <input id="exam-q" className="input" type="search" placeholder="Nome do exame, LOINC ou TUSS (ex.: hemograma)" value={q} onChange={(e) => setQ(e.target.value)} autoComplete="off" />
      {items.length > 0 && (
        <ul className="cid-results" aria-label="Exames encontrados">
          {items.slice(0, 12).map((e) => (
            <li key={e.loinc}>
              <Button
                type="button"
                size="sm"
                onClick={() => {
                  onPick(e);
                  setQ("");
                }}
              >
                <span>
                  <strong>{e.name}</strong> <span className="text-text-3">· LOINC {e.loinc} · TUSS {e.tuss}</span>
                </span>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
