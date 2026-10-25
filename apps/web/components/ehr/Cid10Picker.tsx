"use client";

import { useState } from "react";
import { api } from "@/lib/api";
import type { Code } from "@/lib/ehr";
import { useAsyncEffect } from "@/lib/use-async";
import { Button } from "@/components/ui";

/** CID-10 da Avaliação: busca por código ou palavras (tabela do DATASUS na clinical-api), escolhidos viram etiquetas. */
export function Cid10Picker({ value, onChange, disabled }: { value: Code[]; onChange: (v: Code[]) => void; disabled?: boolean }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<Code[]>([]);

  useAsyncEffect(
    async () => {
      if (q.trim().length < 2) {
        setResults([]);
        return;
      }
      await new Promise((r) => setTimeout(r, 200));
      const r = await api.get<{ items: Code[] }>("/api/v1/clinical/terminology/cid10", { query: { q: q.trim() } });
      setResults(r.items.filter((x) => !value.some((v) => v.code === x.code)).slice(0, 8));
    },
    () => setResults([]),
    [q],
  );

  return (
    <div>
      {value.length > 0 && (
        <ul className="chips" aria-label="CID-10 escolhidos">
          {value.map((c) => (
            <li key={c.code}>
              <span>
                <strong>{c.code}</strong> {c.display}
              </span>
              {!disabled && (
                <button type="button" aria-label={`Remover ${c.code}`} onClick={() => onChange(value.filter((x) => x.code !== c.code))}>
                  ×
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {!disabled && (
        <>
          <label className="sr-only" htmlFor="cid10-q">
            Buscar CID-10
          </label>
          <input id="cid10-q" className="input" type="search" placeholder="CID-10: código (J18) ou palavras (pneumonia)" value={q} onChange={(e) => setQ(e.target.value)} autoComplete="off" />
          {results.length > 0 && (
            <ul className="cid-results" aria-label="Sugestões de CID-10">
              {results.map((c) => (
                <li key={c.code}>
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => {
                      onChange([...value, c]);
                      setQ("");
                    }}
                  >
                    {c.code} · {c.display}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
