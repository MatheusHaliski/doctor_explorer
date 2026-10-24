"use client";

import { useState } from "react";
import { api } from "@/lib/api";
import { BACKGROUNDS, FONT_SCALES, VEIL_PRESETS, setAppearance, type Appearance, type Theme } from "@/lib/appearance";
import { useAsyncEffect } from "@/lib/use-async";
import { Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

type Prefs = Appearance & { updatedAt: string };

const THEMES: Array<{ id: Theme; name: string }> = [
  { id: "system", name: "Sistema" },
  { id: "light", name: "Claro" },
  { id: "dark", name: "Escuro" },
];

/** RF01 · Aparência: arte de fundo, intensidade, tema e tamanho do texto. Vale na hora e sincroniza entre aparelhos. */
export default function AppearancePage() {
  const [prefs, setPrefs] = useState<Appearance | null>(null);
  const [error, setError] = useState<unknown>(null);
  const toast = useToast();

  useAsyncEffect(
    async () => {
      const p = await api.get<Prefs>("/api/v1/me/preferences");
      setPrefs({ theme: p.theme, fontScale: p.fontScale, background: p.background, backgroundVeil: p.backgroundVeil });
    },
    setError,
    [],
  );

  /** Aplica na hora (otimista), salva na API e oferece desfazer; se a API recusar, volta ao anterior. */
  async function change(patch: Partial<Appearance>, label: string) {
    if (!prefs) return;
    const before = prefs;
    const next = { ...prefs, ...patch };
    setPrefs(next);
    setAppearance(next);
    setError(null);
    try {
      await api.put("/api/v1/me/preferences", patch);
      toast.show(label, () => {
        setPrefs(before);
        setAppearance(before);
        api.put("/api/v1/me/preferences", before).catch(setError);
      });
    } catch (err) {
      setPrefs(before);
      setAppearance(before);
      setError(err);
    }
  }

  if (!prefs) return error ? <ProblemAlert error={error} /> : <Loading />;
  const hasArt = prefs.background !== "none";
  const preset = VEIL_PRESETS.find((v) => v.veil === prefs.backgroundVeil)?.id ?? "media";

  return (
    <div className="max-w-3xl">
      <h1>Aparência</h1>
      <p className="lead">Vale na hora e acompanha sua conta em qualquer aparelho. O texto fica sempre num painel de leitura, nunca direto sobre a arte.</p>
      <ProblemAlert error={error} />

      <h2 id="arte">Arte de fundo</h2>
      <div className="choices" role="radiogroup" aria-labelledby="arte">
        {BACKGROUNDS.map((b) => (
          <label key={b.id} className="choice">
            <input type="radio" name="background" value={b.id} checked={prefs.background === b.id} onChange={() => change({ background: b.id }, `Arte de fundo: ${b.name}.`)} />
            <span className="thumb" style={b.id === "none" ? undefined : { backgroundImage: `url(/backgrounds/thumbs/${b.id}.webp)` }} aria-hidden="true" />
            <span className="tick">✓ em uso</span>
            <span className="name">{b.name}</span>
            <span className="desc">{b.description}</span>
          </label>
        ))}
      </div>

      <h2 id="intensidade">Intensidade da arte</h2>
      {hasArt ? (
        <div className="segmented" role="radiogroup" aria-labelledby="intensidade">
          {VEIL_PRESETS.map((v) => (
            <label key={v.id}>
              <input type="radio" name="veil" value={v.id} checked={preset === v.id} onChange={() => change({ backgroundVeil: v.veil }, `Intensidade: ${v.name.toLowerCase()}.`)} />
              {v.name}
            </label>
          ))}
        </div>
      ) : (
        <Notice tone="info">Escolha uma arte para ajustar a intensidade.</Notice>
      )}

      <h2 id="tema">Tema</h2>
      <div className="segmented" role="radiogroup" aria-labelledby="tema">
        {THEMES.map((t) => (
          <label key={t.id}>
            <input type="radio" name="theme" value={t.id} checked={prefs.theme === t.id} onChange={() => change({ theme: t.id }, `Tema: ${t.name.toLowerCase()}.`)} />
            {t.name}
          </label>
        ))}
      </div>

      <h2 id="fonte">Tamanho do texto</h2>
      <div className="segmented" role="radiogroup" aria-labelledby="fonte">
        {FONT_SCALES.map((f) => (
          <label key={f}>
            <input type="radio" name="font" value={f} checked={prefs.fontScale === f} onChange={() => change({ fontScale: f }, `Texto em ${f} %.`)} />
            {f} %
          </label>
        ))}
      </div>
      <p className="mt-6 text-sm text-text-3">Neste aparelho fica só uma cópia para a tela abrir sem piscar; a escolha vale para a sua conta.</p>
      {toast.el}
    </div>
  );
}
