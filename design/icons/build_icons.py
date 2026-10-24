#!/usr/bin/env python3
"""Gera os ícones do Doctor Explorer no estilo do logo: plástico azul com brilho, borda e sombra.

Uso: python3 design/icons/build_icons.py
Saída: design/icons/nav/*.svg e design/icons/topbar/*.svg (grade 64 × 64).

Cada ícone = corpo em azul (gradiente da marca) + brilho especular recortado pelo corpo + detalhe em
branco. A cor vem de tokens fixos aqui porque o SVG precisa funcionar fora do app (favicon, docs,
Trello). No app, o componente React recebe `fill` por prop quando precisa de outro tom.
"""
from pathlib import Path

ROOT = Path(__file__).parent
BLUE_TOP, BLUE_BOTTOM, BLUE_RIM = "#4C97F5", "#0F5BD6", "#0B49B4"

DEFS = f"""  <defs>
    <linearGradient id="b" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{BLUE_TOP}"/><stop offset="1" stop-color="{BLUE_BOTTOM}"/>
    </linearGradient>
    <linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity=".55"/><stop offset=".55" stop-color="#FFFFFF" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="w" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#DCE6F3"/>
    </linearGradient>
    <filter id="s" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="2" stdDeviation="1.6" flood-color="#0B2A5B" flood-opacity=".28"/>
    </filter>
    <clipPath id="c">{{clip}}</clipPath>
  </defs>
"""

TEMPLATE = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64" aria-hidden="true">
  <!-- {comment} -->
{defs}  <g filter="url(#s)">
{body}
  </g>
  <rect clip-path="url(#c)" x="0" y="0" width="64" height="64" fill="url(#g)"/>
{detail}</svg>
"""


def body(d, extra=""):
    """Corpo plástico: preenchimento em gradiente + borda escura fina."""
    return f'    <path d="{d}" fill="url(#b)" stroke="{BLUE_RIM}" stroke-width="1.5" stroke-linejoin="round"{extra}/>'


def white(d, sw=0, fill=True, rim=False):
    """Detalhe em branco; `rim=True` dá borda azul para o branco não sumir sobre fundo claro."""
    f = 'fill="url(#w)"' if fill else 'fill="none"'
    s = f' stroke="#FFFFFF" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"' if sw else ""
    if rim:
        s = f' stroke="{BLUE_RIM}" stroke-width="1.5" stroke-linejoin="round"'
    return f'  <path d="{d}" {f}{s}/>\n'


RING = "M32 6a26 26 0 1 0 0 52a26 26 0 1 0 0-52z"  # disco cheio: o glifo branco fica sobre o azul
RING_CLIP = '<circle cx="32" cy="32" r="26"/>'
SHIELD = "M32 5l22 8v15c0 13-9.5 23-22 30C19.5 51 10 41 10 28V13z"
BUBBLE = "M8 10h48a4 4 0 0 1 4 4v26a4 4 0 0 1-4 4H26l-12 10V44H8a4 4 0 0 1-4-4V14a4 4 0 0 1 4-4z"
DOC = "M14 4h26l12 12v42a3 3 0 0 1-3 3H14a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z"
MAP = "M6 16l17-6 18 6 17-6v38l-17 6-18-6-17 6z"
LUPA = "M27 6a21 21 0 1 1 0 42c-4.3 0-8.3-1.3-11.6-3.5L8 52l-4-4 7.5-7.5A21 21 0 0 1 27 6zm0 8a13 13 0 1 0 0 26a13 13 0 1 0 0-26z"

ICONS = {
    "nav": {
        "inicio": ("Início · mapa dobrado do logo", MAP, '<path d="M6 16l17-6 18 6 17-6v38l-17 6-18-6-17 6z"/>',
                   white("M23 10v38M41 16v38", 2.2, False) + white("M12 30c6-4 10 2 16-2s10 2 14-1", 2.6, False)),
        "casos-clinicos": ("Casos clínicos · mapa + pin com cruz", MAP, '<path d="M6 16l17-6 18 6 17-6v38l-17 6-18-6-17 6z"/>',
                           white("M23 10v38M41 16v38", 2, False)
                           + '  <path d="M45 2a11 11 0 0 1 11 11c0 8-11 18-11 18S34 21 34 13A11 11 0 0 1 45 2z" fill="url(#w)" stroke="#0B49B4" stroke-width="1.5"/>\n'
                           + f'  <path d="M43 8h4v4h4v4h-4v4h-4v-4h-4v-4h4z" fill="{BLUE_BOTTOM}"/>\n'),
        "grupos": ("Grupos · dois profissionais", "M22 12a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM4 54c0-11 8-18 18-18s18 7 18 18zM44 18a7 7 0 1 1 0 14a7 7 0 1 1 0-14zM44 36c9 0 16 6 16 16H44z",
                   '<path d="M22 12a9 9 0 1 1 0 18a9 9 0 1 1 0-18zM4 54c0-11 8-18 18-18s18 7 18 18zM44 18a7 7 0 1 1 0 14a7 7 0 1 1 0-14zM44 36c9 0 16 6 16 16H44z"/>',
                   white("M17 38l5 8 5-8", 2.4, False)),
        "mensagens": ("Mensagens · balão com cadeado (E2EE)", BUBBLE, f'<path d="{BUBBLE}"/>',
                      white("M24 26h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H24a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z")
                      + white("M27 26v-3a5 5 0 0 1 10 0v3", 3, False)),
        "chamadas": ("Chamadas · câmera no anel", RING, RING_CLIP,
                     white("M20 25h18a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H20a3 3 0 0 1-3-3v-8a3 3 0 0 1 3-3z")
                     + white("M41 30l7-4v12l-7-4z")),
        "prontuario": ("Prontuário · prancheta com estetoscópio", "M14 8h8V4h20v4h8a3 3 0 0 1 3 3v47a3 3 0 0 1-3 3H14a3 3 0 0 1-3-3V11a3 3 0 0 1 3-3z",
                       '<path d="M14 8h8V4h20v4h8a3 3 0 0 1 3 3v47a3 3 0 0 1-3 3H14a3 3 0 0 1-3-3V11a3 3 0 0 1 3-3z"/>',
                       white("M22 4h20v8H22z") + white("M22 22v8a10 10 0 0 0 20 0v-8", 3, False)
                       + white("M32 40v6a6 6 0 0 0 12 0", 3, False) + white("M44 44a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7z")),
        "prescricao": ("Prescrição · documento Rx", DOC, f'<path d="{DOC}"/>',
                       white("M40 4v12h12z") + white("M22 28v20M22 28h8a5 5 0 0 1 0 10h-8", 3, False)
                       + white("M30 38l9 10M39 38l-9 10", 3, False)),
        "exames": ("Exames · tubo com lupa", "M20 4h14v24a7 7 0 0 1-14 0z",
                   '<path d="M20 4h14v24a7 7 0 0 1-14 0z"/>',
                   white("M20 10h14") + white("M20 4h14v4H20z")
                   + f'  <path d="M43 32a11 11 0 1 1 0 22a11 11 0 1 1 0-22zm0 6a5 5 0 1 0 0 10a5 5 0 1 0 0-10zM50 51l8 8" fill="url(#b)" stroke="{BLUE_RIM}" stroke-width="1.5"/>\n'
                   + white("M51 52l7 7", 4, False)),
        "instituicoes": ("Instituições · hospital com cruz", "M8 58V22L32 8l24 14v36z",
                         '<path d="M8 58V22L32 8l24 14v36z"/>',
                         white("M29 24h6v8h8v6h-8v8h-6v-8h-8v-6h8z") + white("M26 58V50h12v8z")),
        "administracao": ("Administração · escudo com hierarquia", SHIELD, f'<path d="{SHIELD}"/>',
                          white("M32 16a5 5 0 1 1 0 10a5 5 0 1 1 0-10z") + white("M32 26v8M20 34h24M20 34v6M44 34v6", 3, False)
                          + white("M20 40a4 4 0 1 1 0 8a4 4 0 1 1 0-8zM44 40a4 4 0 1 1 0 8a4 4 0 1 1 0-8z")),
        "privacidade": ("Privacidade · escudo com cadeado", SHIELD, f'<path d="{SHIELD}"/>',
                        white("M23 28h18a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H23a2 2 0 0 1-2-2V30a2 2 0 0 1 2-2z")
                        + white("M26 28v-5a6 6 0 0 1 12 0v5", 3.5, False)),
        "configuracoes": ("Configurações · engrenagem",
                          "M28 4h8l1.5 6.5 5-2.5 5.5 5.5-2.5 5L52 20v8l-6.5 1.5 2.5 5-5.5 5.5-5-2.5L36 44h-8l-1.5-6.5-5 2.5-5.5-5.5 2.5-5L12 28v-8l6.5-1.5-2.5-5 5.5-5.5 5 2.5zM32 16a8 8 0 1 0 0 16a8 8 0 1 0 0-16z",
                          '<path d="M28 4h8l1.5 6.5 5-2.5 5.5 5.5-2.5 5L52 20v8l-6.5 1.5 2.5 5-5.5 5.5-5-2.5L36 44h-8l-1.5-6.5-5 2.5-5.5-5.5 2.5-5L12 28v-8l6.5-1.5-2.5-5 5.5-5.5 5 2.5z"/>',
                          white("M32 19a5 5 0 1 1 0 10a5 5 0 1 1 0-10z")),
    },
    "topbar": {
        "buscar": ("Buscar · lupa do logo", LUPA, '<path d="M27 6a21 21 0 1 1 0 42a21 21 0 1 1 0-42z"/>',
                   white("M27 14a13 13 0 1 0 0 26a13 13 0 1 0 0-26z", rim=True) + '  <path d="M18 22a9 9 0 0 1 9-7" fill="none" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round" opacity=".9"/>\n'),
        "notificacoes": ("Notificações · sino", "M32 4a4 4 0 0 1 4 4v1.5A18 18 0 0 1 50 27v13l6 8H8l6-8V27A18 18 0 0 1 28 9.5V8a4 4 0 0 1 4-4zM24 52h16a8 8 0 0 1-16 0z",
                         '<path d="M32 4a4 4 0 0 1 4 4v1.5A18 18 0 0 1 50 27v13l6 8H8l6-8V27A18 18 0 0 1 28 9.5V8a4 4 0 0 1 4-4z"/>',
                         white("M24 52h16a8 8 0 0 1-16 0z")),
        "nova-mensagem": ("Nova mensagem · balão com +", BUBBLE, f'<path d="{BUBBLE}"/>',
                          white("M29 18h6v6h6v6h-6v6h-6v-6h-6v-6h6z")),
        "e2ee": ("Criptografada ponta a ponta · cadeado", "M14 26h36a4 4 0 0 1 4 4v24a4 4 0 0 1-4 4H14a4 4 0 0 1-4-4V30a4 4 0 0 1 4-4z",
                 '<path d="M14 26h36a4 4 0 0 1 4 4v24a4 4 0 0 1-4 4H14a4 4 0 0 1-4-4V30a4 4 0 0 1 4-4z"/>',
                 f'  <path d="M20 26V18a12 12 0 0 1 24 0v8" fill="none" stroke="{BLUE_RIM}" stroke-width="6" stroke-linecap="round"/>\n'
                 + f'  <path d="M20 26V18a12 12 0 0 1 24 0v8" fill="none" stroke="url(#b)" stroke-width="4" stroke-linecap="round"/>\n'
                 + white("M32 38a4 4 0 0 1 2 7.5V50h-4v-4.5A4 4 0 0 1 32 38z")),
        "selo-verificado": ("Selo verificado · anel com check", RING, RING_CLIP, white("M20 33l8 8 16-18", 5, False)),
        "selo-pendente": ("Selo em análise · anel com relógio", RING, RING_CLIP, white("M32 20v13l8 5", 5, False)),
        "selo-rejeitado": ("Selo não verificado · anel com X", RING, RING_CLIP, white("M24 24l16 16M40 24L24 40", 5, False)),
        "ajuda": ("Ajuda · interrogação no anel", RING, RING_CLIP,
                  white("M25 27a7 7 0 1 1 9.5 6.5c-1.8.8-2.5 2-2.5 4V39", 4.5, False) + white("M32 44a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z")),
        "perfil": ("Perfil · profissional de jaleco", "M6 58c0-13 11-22 26-22s26 9 26 22z",
                   '<path d="M6 58c0-13 11-22 26-22s26 9 26 22z"/>',
                   white("M32 6a14 14 0 1 1 0 28a14 14 0 1 1 0-28z", rim=True) + white("M26 36l6 12 6-12z") + white("M22 37l10 21M42 37L32 58", 3, False)),
        "menu": ("Menu · três barras", "M6 10h52a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8zM6 28h52a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8zM6 46h52a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8z",
                 '<path d="M6 10h52a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8zM6 28h52a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8zM6 46h52a4 4 0 0 1 0 8H6a4 4 0 0 1 0-8z"/>', ""),
        "modo-plantao": ("Modo plantão · lua", "M38 4a26 26 0 1 0 22 36A22 22 0 0 1 38 4z",
                         '<path d="M38 4a26 26 0 1 0 22 36A22 22 0 0 1 38 4z"/>',
                         white("M50 6v10M45 11h10", 3, False)),
        "sair": ("Sair · porta com seta", "M8 8h26a3 3 0 0 1 3 3v8h-8v-5H16v36h13v-5h8v8a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V11a3 3 0 0 1 3-3z",
                 '<path d="M8 8h26a3 3 0 0 1 3 3v8h-8v-5H16v36h13v-5h8v8a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V11a3 3 0 0 1 3-3z"/>',
                 f'  <path d="M28 32h26M46 22l10 10-10 10" fill="none" stroke="{BLUE_RIM}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>\n'
                 + '  <path d="M28 32h26M46 22l10 10-10 10" fill="none" stroke="url(#b)" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>\n'),
    },
}


def main():
    n = 0
    for folder, icons in ICONS.items():
        (ROOT / folder).mkdir(exist_ok=True)
        for name, (comment, d, clip, detail) in icons.items():
            svg = TEMPLATE.format(comment=comment, defs=DEFS.format(clip=clip), body=body(d), detail=detail)
            (ROOT / folder / f"{name}.svg").write_text(svg)
            n += 1
    print(f"{n} ícones gerados")


if __name__ == "__main__":
    main()
