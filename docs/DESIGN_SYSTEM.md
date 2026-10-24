# Design System · tokens e contraste

Fonte dos tokens: `design/prototype/tokens.css` (no monorepo, `packages/tokens` em DTCG 2025.10 →
Style Dictionary 5 → `tokens.css` para a web e `tokens.ts` para o mobile). Três níveis: **primitivos →
semânticos → componentes**. Tema claro é o padrão; escuro por `prefers-color-scheme` ou
`data-theme="dark"`; `prefers-reduced-motion` zera as durações.

## Paleta derivada do logo

| Elemento do logo | Primitivo | Uso semântico |
|---|---|---|
| Anel e faixa azuis | `--color-brand-500` `#1565E0` | `--brand` (ícones, chips ativos, botão primário) |
| Azul mais escuro para texto | `--color-brand-600` `#0F4FBF` | `--brand-text` (links, item ativo) |
| Mapa verde | `--color-green-700` `#187033` | `--success`, `--seal-verified` |
| Pin vermelho | `--color-red-700` `#B3261E` | `--danger` (só erro e ação destrutiva) |
| Fundo off-white | `--color-neutral-50` `#F6F4EF` | `--surface` |

## Regras

- Todo par texto/fundo ≥ **4,5:1**; texto grande e elementos não textuais ≥ **3:1**. O CI roda
  `python3 scripts/wcag_contrast.py` e falha se algum par cair abaixo.
- A cor nunca é o único sinal: selo, estado e erro levam cor + ícone + texto.
- Foco visível (`--focus-ring`, 3 px) em todo controle; alvo de toque ≥ 44 px (`--tap-min`); fonte
  ajustável de 85 a 150 % sem quebrar layout (tudo em `rem`).
- Vermelho (`--danger`) nunca em texto normal (heurística 8).

## Tabela de contraste (gerada por `scripts/wcag_contrast.py --md`)

| Par | Uso | Mínimo | Claro | Escuro |
|---|---|---|---|---|
| `--text` / `--bg` | texto principal sobre fundo | 4.5:1 | 16.37:1 ✅ | 16.14:1 ✅ |
| `--text` / `--surface` | texto principal sobre superfície | 4.5:1 | 14.90:1 ✅ | 14.72:1 ✅ |
| `--text-2` / `--bg` | texto secundário sobre fundo | 4.5:1 | 9.66:1 ✅ | 11.11:1 ✅ |
| `--text-2` / `--surface` | texto secundário sobre superfície | 4.5:1 | 8.78:1 ✅ | 10.13:1 ✅ |
| `--text-3` / `--bg` | texto terciário sobre fundo | 4.5:1 | 5.81:1 ✅ | 7.28:1 ✅ |
| `--text-3` / `--surface` | texto terciário sobre superfície | 4.5:1 | 5.29:1 ✅ | 6.64:1 ✅ |
| `--brand-text` / `--bg` | link/marca como texto sobre fundo | 4.5:1 | 7.26:1 ✅ | 9.05:1 ✅ |
| `--brand-text` / `--surface` | link/marca como texto sobre superfície | 4.5:1 | 6.61:1 ✅ | 8.25:1 ✅ |
| `--nav-active-text` / `--nav-active-bg` | item ativo da navegação | 4.5:1 | 6.15:1 ✅ | 6.75:1 ✅ |
| `--on-brand` / `--brand` | texto sobre botão de marca | 4.5:1 | 5.29:1 ✅ | 7.00:1 ✅ |
| `--chip-active-text` / `--chip-active-bg` | chip de filtro ativo | 4.5:1 | 5.29:1 ✅ | 7.00:1 ✅ |
| `--on-danger` / `--danger` | texto sobre botão destrutivo | 4.5:1 | 6.54:1 ✅ | 6.77:1 ✅ |
| `--success` / `--bg` | texto de sucesso | 4.5:1 | 6.17:1 ✅ | 9.73:1 ✅ |
| `--warning` / `--bg` | texto de aviso | 4.5:1 | 5.93:1 ✅ | 11.24:1 ✅ |
| `--danger` / `--bg` | texto de erro | 4.5:1 | 6.54:1 ✅ | 7.95:1 ✅ |
| `--info` / `--bg` | texto informativo | 4.5:1 | 7.26:1 ✅ | 9.05:1 ✅ |
| `--seal-verified` / `--bg` | selo verificado (texto) | 4.5:1 | 6.17:1 ✅ | 9.73:1 ✅ |
| `--seal-pending` / `--bg` | selo em análise (texto) | 4.5:1 | 5.93:1 ✅ | 11.24:1 ✅ |
| `--seal-rejected` / `--bg` | selo não verificado (texto) | 4.5:1 | 6.54:1 ✅ | 7.95:1 ✅ |
| `--success` / `--success-soft` | sucesso sobre fundo suave | 4.5:1 | 5.39:1 ✅ | 7.54:1 ✅ |
| `--warning` / `--warning-soft` | aviso sobre fundo suave | 4.5:1 | 5.25:1 ✅ | 8.33:1 ✅ |
| `--danger` / `--danger-soft` | erro sobre fundo suave | 4.5:1 | 5.38:1 ✅ | 6.77:1 ✅ |
| `--focus-ring` / `--bg` | anel de foco sobre fundo (não textual) | 3.0:1 | 9.61:1 ✅ | 10.00:1 ✅ |
| `--focus-ring` / `--surface` | anel de foco sobre superfície (não textual) | 3.0:1 | 8.74:1 ✅ | 9.12:1 ✅ |
| `--brand` / `--bg` | ícone/borda de marca sobre fundo (não textual) | 3.0:1 | 5.29:1 ✅ | 7.41:1 ✅ |
| `--brand` / `--surface` | ícone/borda de marca sobre superfície (não textual) | 3.0:1 | 4.81:1 ✅ | 6.76:1 ✅ |
| `--line` / `--bg` | linha divisória (decorativa, sem mínimo) | 1.0:1 | 1.35:1 ✅ | 1.44:1 ✅ |
