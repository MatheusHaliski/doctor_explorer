import { describe, expect, it } from "vitest";
import { APPEARANCE_BOOT_SCRIPT, BACKGROUNDS, DEFAULT_APPEARANCE, VEIL_PRESETS, applyAppearance } from "@/lib/appearance";

/** <html> mínimo: dataset + style, o suficiente para applyAppearance e o script de boot. */
function fakeRoot() {
  const props: Record<string, string> = {};
  return {
    dataset: {} as Record<string, string | undefined>,
    style: { fontSize: "", setProperty: (k: string, v: string) => (props[k] = v), props },
  };
}

describe("aparência", () => {
  it("aplica tema, arte, véu e fonte no <html>", () => {
    const root = fakeRoot();
    applyAppearance({ theme: "dark", fontScale: 125, background: "ambar", backgroundVeil: 20 }, root as unknown as HTMLElement);
    expect(root.dataset.theme).toBe("dark");
    expect(root.dataset.bg).toBe("ambar");
    expect(root.style.props["--bg-veil"]).toBe("20%");
    expect(root.style.fontSize).toBe("125%");
    applyAppearance(DEFAULT_APPEARANCE, root as unknown as HTMLElement);
    expect(root.dataset.theme).toBeUndefined(); // segue o sistema
    expect(root.dataset.bg).toBe("none");
    expect(root.style.fontSize).toBe("");
  });

  it("lista as mesmas artes e limites da API", () => {
    expect(BACKGROUNDS.map((b) => b.id)).toEqual(["none", "aurora-clara", "neon-escuro", "azul-noturno", "ambar", "neon-colorido"]);
    for (const v of VEIL_PRESETS) expect(v.veil).toBeGreaterThanOrEqual(20);
    for (const v of VEIL_PRESETS) expect(v.veil).toBeLessThanOrEqual(80);
  });

  it("script de boot aplica a cópia local e ignora lixo", () => {
    const run = (stored: string | null) => {
      const root = fakeRoot();
      const localStorage = { getItem: () => stored };
      new Function("localStorage", "document", APPEARANCE_BOOT_SCRIPT)(localStorage, { documentElement: root });
      return root;
    };
    const r = run(JSON.stringify({ theme: "light", background: "neon-escuro", backgroundVeil: 70, fontScale: 112 }));
    expect(r.dataset).toEqual({ theme: "light", bg: "neon-escuro" });
    expect(r.style.props["--bg-veil"]).toBe("70%");
    expect(r.style.fontSize).toBe("112%");
    expect(run("{não é json").dataset).toEqual({});
    expect(run(null).dataset).toEqual({});
  });
});
