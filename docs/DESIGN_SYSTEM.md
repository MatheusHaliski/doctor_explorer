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

## Artes de fundo (Aparência)

Cinco artes de ícones de saúde, escolhidas em **Aparência** e salvas na conta: Aurora clara, Neon escuro, Azul
noturno, Âmbar e Neon colorido (originais em `design/brand/backgrounds/`; `build_thumbs.py` copia para
`apps/web/public/backgrounds/` e gera miniaturas 360×240). "Sem arte" é o padrão.

Camadas, de trás para a frente:

1. **Arte** cobrindo a página (`.app-bg`, fixa, decorativa, `aria-hidden`).
2. **Véu** da cor de superfície do tema com opacidade `--bg-veil`: Forte 20 %, Média 45 %, Suave 70 %
   (a API aceita 20–80).
3. **Painel de leitura** (`main`) com 92 % da superfície e desfoque de 10 px. Todo texto fica aqui ou em cards
   opacos, nunca direto sobre a arte. Com `prefers-reduced-transparency`, o painel fica opaco.

Contraste com a arte mais forte (véu 20 %) e o painel a 92 %, nos pixels mais claros e mais escuros de cada arte
(`python3 scripts/background_contrast.py --md`, roda no CI):

| Arte | Tema | text | text-2 | text-3 | brand-text |
|---|---|---|---|---|---|
| aurora-clara | claro | 13.3:1 | 7.9:1 | 4.7:1 | 5.9:1 |
| aurora-clara | escuro | 13.3:1 | 9.1:1 | 6.0:1 | 7.4:1 |
| neon-escuro | claro | 13.0:1 | 7.7:1 | 4.6:1 | 5.8:1 |
| neon-escuro | escuro | 13.6:1 | 9.4:1 | 6.1:1 | 7.6:1 |
| azul-noturno | claro | 13.3:1 | 7.8:1 | 4.7:1 | 5.9:1 |
| azul-noturno | escuro | 13.5:1 | 9.3:1 | 6.1:1 | 7.6:1 |
| ambar | claro | 13.6:1 | 8.0:1 | 4.8:1 | 6.0:1 |
| ambar | escuro | 13.0:1 | 9.0:1 | 5.9:1 | 7.3:1 |
| neon-colorido | claro | 13.1:1 | 7.7:1 | 4.7:1 | 5.8:1 |
| neon-colorido | escuro | 13.4:1 | 9.2:1 | 6.1:1 | 7.5:1 |

Um véu sozinho não basta: sem o painel, `text-3` no tema claro cai para 3,4:1 mesmo com véu de 80 %.

## Gráficos (RF13)

- Séries categóricas: `--series-1` (azul `#2a78d6` / escuro `#3987e5`) e `--series-2` (laranja `#eb6834` /
  escuro `#d95926`), nessa ordem fixa. Validadas com o script do método de visualização: separação para
  daltonismo ΔE 24,7 (claro) e 26,8 (escuro), contraste ≥ 3:1 com a superfície.
- Texto nunca usa a cor da série; legenda sempre visível com duas séries; dica no hover e no foco; tabela
  alternativa em todo gráfico; colunas de no máximo 24 px com topo arredondado de 4 px e 2 px entre colunas.
- Célula suprimida por k-anonimato (menos de 10 pessoas) não vira marca: aparece "<10" e "suprimido" na tabela.

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
