#!/usr/bin/env python3
"""Contraste do texto sobre as artes de fundo (Aparência).

Modelo de camadas (igual ao CSS de apps/web/app/globals.css):
1. a arte cobre a página;
2. um véu da cor de superfície do tema, com opacidade `veil` (a "intensidade" escolhida pelo usuário:
   quanto menor o véu, mais a arte aparece);
3. o conteúdo fica num painel de leitura com a cor de superfície a `panel` de opacidade (e desfoque).
   Texto nunca fica direto sobre a arte, só sobre o painel.

Para cada arte e tema, mede o contraste WCAG dos tokens de texto contra o painel nos pixels mais claros e
mais escuros da arte (percentis 1 e 99 de luminância, depois de um desfoque que imita o backdrop-filter).
Falha se algum token ficar abaixo de 4,5:1 com o menor véu permitido.

Uso: python3 scripts/background_contrast.py [--md] [--min-veil 30] [--panel 94]
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
BG_DIR = ROOT / "apps" / "web" / "public" / "backgrounds"
ARTS = ["aurora-clara", "neon-escuro", "azul-noturno", "ambar", "neon-colorido"]
# superfície e textos de cada tema (design/prototype/tokens.css)
THEMES = {
    "claro": {"surface": "#F6F4EF", "text": "#17202E", "text-2": "#3B4557", "text-3": "#5B6677", "brand-text": "#0F4FBF"},
    "escuro": {"surface": "#171F2C", "text": "#EEF2F7", "text-2": "#C3CBD8", "text-3": "#9AA5B5", "brand-text": "#8FB8FF"},
}


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lum(rgb):
    def ch(c):
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def blend(art, surface, dim):
    return tuple(round(s * dim + a * (1 - dim)) for a, s in zip(art, surface))


def extremes(path):
    im = Image.open(path).convert("RGB").resize((300, 224)).filter(ImageFilter.GaussianBlur(2))
    px = sorted(im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata(), key=lum)
    return px[len(px) // 100], px[len(px) * 99 // 100]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--md", action="store_true")
    ap.add_argument("--min-veil", type=int, default=30)
    ap.add_argument("--panel", type=int, default=94)
    a = ap.parse_args()
    veil, panel = a.min_veil / 100, a.panel / 100
    rows, fails = [], []
    for art in ARTS:
        dark_px, light_px = extremes(BG_DIR / f"{art}.webp")
        for theme, t in THEMES.items():
            surface = hex_rgb(t["surface"])
            worst = {}
            for token in ("text", "text-2", "text-3", "brand-text"):
                fg = hex_rgb(t[token])
                worst[token] = min(ratio(fg, blend(blend(p, surface, veil), surface, panel)) for p in (dark_px, light_px))
                if worst[token] < 4.5:
                    fails.append(f"{art}/{theme}/{token}: {worst[token]:.2f}:1")
            rows.append((art, theme, worst))
    if a.md:
        print(f"| Arte | Tema | text | text-2 | text-3 | brand-text |\n|---|---|---|---|---|---|")
        for art, theme, w in rows:
            print(f"| {art} | {theme} | " + " | ".join(f"{w[k]:.1f}:1" for k in ("text", "text-2", "text-3", "brand-text")) + " |")
    else:
        for art, theme, w in rows:
            print(f"{art:14} {theme:7} " + "  ".join(f"{k}={w[k]:.2f}" for k in ("text", "text-2", "text-3", "brand-text")))
    print(f"véu mínimo {a.min_veil}% · painel {a.panel}%: " + ("ok" if not fails else "FALHA " + "; ".join(fails)))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
