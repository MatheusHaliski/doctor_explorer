# Ícones do Doctor Explorer

Conjunto próprio derivado do logo oficial (`design/brand/logo-doctor-explorer.svg`): **plástico azul**
com gradiente da marca, brilho especular, borda escura fina e sombra suave, igual ao anel e à faixa do
logo. Gerados por `build_icons.py`; não edite os SVGs à mão.

```bash
python3 design/icons/build_icons.py   # regenera nav/*.svg e topbar/*.svg
```

Entram no app via **SVGR** em `packages/ui-web` e `react-native-svg` em `packages/ui-native`. O Lucide
continua para ícones genéricos (seta, fechar, copiar) e para estados em que o plástico pesaria (listas
densas, tabelas).

## Linguagem visual

| Elemento do logo | Vira |
|---|---|
| Mapa dobrado | `nav/inicio`, `nav/casos-clinicos` |
| Pin com cruz | `nav/casos-clinicos` (pin branco com cruz azul), `nav/instituicoes` (cruz) |
| Lupa | `topbar/buscar`, `nav/exames` |
| Estetoscópio | `nav/prontuario` |
| Profissional de jaleco | `nav/grupos`, `topbar/perfil` |
| Disco/anel azul | `nav/chamadas`, `topbar/selo-*`, `topbar/ajuda` |

Receita do material (em `build_icons.py`):

1. **Corpo**: gradiente vertical `#4C97F5 → #0F5BD6`, borda `#0B49B4` de 1,5 px, sombra `dy=2, blur=1.6`.
2. **Brilho**: retângulo branco com gradiente (55% → 0%) recortado pelo corpo (`clipPath`), só na metade
   de cima.
3. **Detalhe**: glifo branco (`#FFFFFF → #DCE6F3`); quando cai sobre fundo claro, recebe borda azul
   (`rim=True`) para não sumir.

Regras:

- Grade **64 × 64**, exibida a 24–32 px na interface. Alvo de toque mínimo de **44 px** (`--tap-min`).
- **A cor nunca é o único sinal** (WCAG 1.4.1): os três selos têm formas diferentes (check, relógio, X)
  e sempre vêm com rótulo em texto; o chip do selo usa `--seal-verified/pending/rejected` no texto e na
  borda, o ícone fica azul.
- Vermelho é reservado a erro e ação destrutiva (heurística 8); por isso o pin e a cruz dos ícones são
  azuis e brancos, não vermelhos como no logo.
- Contraste do ícone contra o fundo ≥ **3:1** (WCAG 1.4.11): o azul `#0F5BD6` passa sobre branco e sobre
  `--surface`; no tema escuro o componente troca os stops para `#6FA6FF → #2F7BE8`.
- Todo ícone é `aria-hidden="true"`; o texto acessível vem do rótulo do botão ou do `aria-label`.
- `prefers-reduced-motion`: nenhum ícone anima.

## Barra lateral (`nav/`)

| Arquivo | Rótulo | Rota |
|---|---|---|
| `inicio.svg` | Início | `/` |
| `casos-clinicos.svg` | Casos clínicos | `/casos` |
| `grupos.svg` | Grupos | `/grupos` |
| `mensagens.svg` | Mensagens | `/mensagens` |
| `chamadas.svg` | Chamadas | `/chamadas` |
| `prontuario.svg` | Prontuário | `/prontuario` |
| `prescricao.svg` | Prescrições | `/prescricoes` |
| `exames.svg` | Exames | `/exames` |
| `instituicoes.svg` | Instituições | `/instituicoes` |
| `administracao.svg` | Administração | `/admin` (só gestor/moderador) |
| `privacidade.svg` | Privacidade | `/privacidade` |
| `configuracoes.svg` | Configurações | `/configuracoes` |

## Topbar (`topbar/`)

| Arquivo | Uso |
|---|---|
| `menu.svg` | abre a barra lateral no mobile |
| `buscar.svg` | busca global (`/` abre; heurística 6) |
| `nova-mensagem.svg` | atalho para nova conversa |
| `notificacoes.svg` | sino com contador |
| `e2ee.svg` | indicador "Criptografada ponta a ponta" numa conversa ou chamada |
| `selo-verificado.svg`, `selo-pendente.svg`, `selo-rejeitado.svg` | estado do selo ao lado do nome |
| `modo-plantao.svg` | liga o modo plantão (notificações só urgentes) |
| `ajuda.svg` | ajuda por tarefa, abre painel lateral |
| `perfil.svg` | menu da conta |
| `sair.svg` | encerrar sessão |

Marca compacta para o topo da barra lateral e favicon: `design/brand/logo-mark.svg`.

Prévia: `preview.html` (renderizada em `preview.png` por Playwright).
