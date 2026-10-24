/**
 * Aparência (RF01, preferências da conta): tema, tamanho da fonte e arte de fundo com intensidade.
 * A fonte da verdade é a API (`/api/v1/me/preferences`, sincroniza entre aparelhos). Uma cópia fica no
 * localStorage só para aplicar antes do primeiro paint (sem piscar); não é dado pessoal.
 *
 * Camadas no CSS (globals.css): arte → véu da cor de superfície (`--bg-veil`) → painel de leitura a 92 %.
 * O texto nunca fica direto sobre a arte; `scripts/background_contrast.py` confere 4,5:1 no CI.
 */

export type Theme = "system" | "light" | "dark";
export type Appearance = { theme: Theme; fontScale: number; background: string; backgroundVeil: number };

export const DEFAULT_APPEARANCE: Appearance = { theme: "system", fontScale: 100, background: "none", backgroundVeil: 45 };

export const BACKGROUNDS: Array<{ id: string; name: string; description: string }> = [
  { id: "none", name: "Sem arte", description: "Fundo liso, o mais calmo para plantões longos." },
  { id: "aurora-clara", name: "Aurora clara", description: "Ícones de saúde em verde-azulado suave." },
  { id: "neon-escuro", name: "Neon escuro", description: "Ícones em neon sobre petróleo, com relevo." },
  { id: "azul-noturno", name: "Azul noturno", description: "Neon azul e verde sobre azul profundo." },
  { id: "ambar", name: "Âmbar", description: "Ícones dourados sobre âmbar quente." },
  { id: "neon-colorido", name: "Neon colorido", description: "Azul, verde e laranja em alto brilho." },
];

/** Intensidade da arte = opacidade do véu: quanto menor o véu, mais a arte aparece. Limites iguais aos da API. */
export const VEIL_PRESETS: Array<{ id: string; name: string; veil: number }> = [
  { id: "forte", name: "Forte", veil: 20 },
  { id: "media", name: "Média", veil: 45 },
  { id: "suave", name: "Suave", veil: 70 },
];

export const FONT_SCALES = [85, 100, 112, 125, 150];

export const APPEARANCE_KEY = "de-appearance";
const EVENT = "de-appearance-change";

/** Aplica no <html>: data-theme (ausente = segue o sistema), data-bg, --bg-veil e o tamanho base da fonte. */
export function applyAppearance(a: Appearance, root: HTMLElement = document.documentElement) {
  if (a.theme === "system") delete root.dataset.theme;
  else root.dataset.theme = a.theme;
  root.dataset.bg = a.background;
  root.style.setProperty("--bg-veil", `${a.backgroundVeil}%`);
  root.style.fontSize = a.fontScale === 100 ? "" : `${a.fontScale}%`;
}

export function cacheAppearance(a: Appearance) {
  try {
    localStorage.setItem(APPEARANCE_KEY, JSON.stringify(a));
  } catch {}
}

export function readCachedAppearance(): Appearance {
  try {
    const raw = localStorage.getItem(APPEARANCE_KEY);
    if (raw) return { ...DEFAULT_APPEARANCE, ...(JSON.parse(raw) as Partial<Appearance>) };
  } catch {}
  return DEFAULT_APPEARANCE;
}

/** Aplica, guarda a cópia local e avisa quem escuta (topbar, tela de Aparência). */
export function setAppearance(a: Appearance) {
  applyAppearance(a);
  cacheAppearance(a);
  window.dispatchEvent(new Event(EVENT));
}

export function subscribeAppearance(cb: () => void) {
  window.addEventListener(EVENT, cb);
  return () => window.removeEventListener(EVENT, cb);
}

export function isDark(): boolean {
  if (typeof document === "undefined") return false;
  const t = document.documentElement.dataset.theme;
  if (t) return t === "dark";
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

/**
 * Script inline do layout raiz: aplica a cópia local antes do primeiro paint.
 * Mantém a mesma lógica de `applyAppearance` (sem import, roda antes do React).
 */
export const APPEARANCE_BOOT_SCRIPT = `try{var a=JSON.parse(localStorage.getItem(${JSON.stringify(APPEARANCE_KEY)})||"null");var r=document.documentElement;if(a){if(a.theme==="light"||a.theme==="dark")r.dataset.theme=a.theme;if(typeof a.background==="string")r.dataset.bg=a.background;if(a.backgroundVeil)r.style.setProperty("--bg-veil",a.backgroundVeil+"%");if(a.fontScale&&a.fontScale!==100)r.style.fontSize=a.fontScale+"%"}}catch(e){}`;
