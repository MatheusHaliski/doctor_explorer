#!/usr/bin/env python3
"""Confere o contraste WCAG dos pares texto/fundo definidos nos tokens.

Uso: python3 scripts/wcag_contrast.py [--md]   (--md imprime a tabela para docs/DESIGN_SYSTEM.md)
Lê design/prototype/tokens.css, resolve var() nos dois temas e falha (exit 1) se algum par ficar abaixo
do mínimo: 4,5:1 para texto, 3:1 para texto grande e elementos não textuais (WCAG 1.4.3, 1.4.11).
"""
import re
import sys
from pathlib import Path

CSS = Path(__file__).resolve().parent.parent / "design" / "prototype" / "tokens.css"

# (primeiro plano, fundo, mínimo, descrição)
PAIRS = [
    ("--text", "--bg", 4.5, "texto principal sobre fundo"),
    ("--text", "--surface", 4.5, "texto principal sobre superfície"),
    ("--text-2", "--bg", 4.5, "texto secundário sobre fundo"),
    ("--text-2", "--surface", 4.5, "texto secundário sobre superfície"),
    ("--text-3", "--bg", 4.5, "texto terciário sobre fundo"),
    ("--text-3", "--surface", 4.5, "texto terciário sobre superfície"),
    ("--brand-text", "--bg", 4.5, "link/marca como texto sobre fundo"),
    ("--brand-text", "--surface", 4.5, "link/marca como texto sobre superfície"),
    ("--nav-active-text", "--nav-active-bg", 4.5, "item ativo da navegação"),
    ("--on-brand", "--brand", 4.5, "texto sobre botão de marca"),
    ("--chip-active-text", "--chip-active-bg", 4.5, "chip de filtro ativo"),
    ("--on-danger", "--danger", 4.5, "texto sobre botão destrutivo"),
    ("--success", "--bg", 4.5, "texto de sucesso"),
    ("--warning", "--bg", 4.5, "texto de aviso"),
    ("--danger", "--bg", 4.5, "texto de erro"),
    ("--info", "--bg", 4.5, "texto informativo"),
    ("--seal-verified", "--bg", 4.5, "selo verificado (texto)"),
    ("--seal-pending", "--bg", 4.5, "selo em análise (texto)"),
    ("--seal-rejected", "--bg", 4.5, "selo não verificado (texto)"),
    ("--success", "--success-soft", 4.5, "sucesso sobre fundo suave"),
    ("--warning", "--warning-soft", 4.5, "aviso sobre fundo suave"),
    ("--danger", "--danger-soft", 4.5, "erro sobre fundo suave"),
    ("--focus-ring", "--bg", 3.0, "anel de foco sobre fundo (não textual)"),
    ("--focus-ring", "--surface", 3.0, "anel de foco sobre superfície (não textual)"),
    ("--brand", "--bg", 3.0, "ícone/borda de marca sobre fundo (não textual)"),
    ("--brand", "--surface", 3.0, "ícone/borda de marca sobre superfície (não textual)"),
    ("--line", "--bg", 1.0, "linha divisória (decorativa, sem mínimo)"),
]


def parse(css: str):
    """Devolve {tema: {token: valor}} resolvendo var() em cada bloco."""
    blocks = {
        "claro": re.search(r":root\s*\{(.*?)\n\}", css, re.S).group(1),
        "escuro": re.search(r":root\[data-theme=\"dark\"\]\s*\{(.*?)\n\}", css, re.S).group(1),
    }
    base = dict(re.findall(r"(--[\w-]+)\s*:\s*([^;]+);", blocks["claro"]))
    themes = {}
    for name, body in blocks.items():
        tokens = dict(base)
        tokens.update(dict(re.findall(r"(--[\w-]+)\s*:\s*([^;]+);", body)))

        def resolve(v, depth=0):
            m = re.fullmatch(r"var\((--[\w-]+)\)", v.strip())
            if m and depth < 10:
                return resolve(tokens[m.group(1)], depth + 1)
            return v.strip()

        themes[name] = {k: resolve(v) for k, v in tokens.items()}
    return themes


def luminance(hexcolor: str) -> float:
    h = hexcolor.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))

    def lin(c):
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def ratio(fg: str, bg: str) -> float:
    l1, l2 = sorted((luminance(fg), luminance(bg)), reverse=True)
    return (l1 + 0.05) / (l2 + 0.05)


def main() -> int:
    themes = parse(CSS.read_text())
    md = "--md" in sys.argv
    failed = 0
    if md:
        print("| Par | Uso | Mínimo | Claro | Escuro |")
        print("|---|---|---|---|---|")
    for fg, bg, minimum, desc in PAIRS:
        cells = []
        for theme in ("claro", "escuro"):
            t = themes[theme]
            r = ratio(t[fg], t[bg])
            ok = r >= minimum
            failed += 0 if ok else 1
            cells.append(f"{r:.2f}:1 {'✅' if ok else '❌'}")
            if not md and not ok:
                print(f"FALHA [{theme}] {fg} ({t[fg]}) sobre {bg} ({t[bg]}): {r:.2f}:1 < {minimum}:1 · {desc}")
        if md:
            print(f"| `{fg}` / `{bg}` | {desc} | {minimum}:1 | {cells[0]} | {cells[1]} |")
    if not md:
        print(f"{len(PAIRS) * 2} verificações, {failed} falhas")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
