/** Tema claro/escuro: preferência do aparelho, nunca dado pessoal. Aplicado antes da hidratação pelo script do layout. */
const EVENT = "de-theme-change";
export const THEME_KEY = "de-theme";

export function isDark(): boolean {
  if (typeof document === "undefined") return false;
  const t = document.documentElement.dataset.theme;
  if (t) return t === "dark";
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function setDark(dark: boolean) {
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  try {
    localStorage.setItem(THEME_KEY, dark ? "dark" : "light");
  } catch {}
  window.dispatchEvent(new Event(EVENT));
}

export function subscribeTheme(cb: () => void) {
  window.addEventListener(EVENT, cb);
  return () => window.removeEventListener(EVENT, cb);
}

/** Script inline (sem dependência) que aplica o tema salvo antes do primeiro paint. */
export const THEME_BOOT_SCRIPT = `try{var t=localStorage.getItem(${JSON.stringify(THEME_KEY)});if(t==="dark"||t==="light")document.documentElement.dataset.theme=t}catch(e){}`;
