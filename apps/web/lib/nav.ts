/**
 * Navegação lateral em grupos: cada subpágina (filtro do feed, aba do painel, aparelhos do chat…) tem um link
 * próprio, com endereço próprio, em vez de ficar escondida dentro da página.
 */
export type NavItem = {
  href: string;
  label: string;
  icon: string;
  /** Outros prefixos de caminho que também acendem este item (ex.: o documento aberto acende "Receitas e pedidos"). */
  also?: string[];
  /** Só o caminho exato (ex.: /perfil é o seu perfil; /perfil/{id} é o de outra pessoa). */
  exact?: boolean;
  soon?: boolean;
};
export type NavGroup = { id: string; label: string; items: NavItem[]; mod?: boolean; panel?: boolean };

const I = (name: string) => `/icons/nav/${name}.svg`;
const NEW = "/icons/topbar/nova-mensagem.svg";

export const NAV: NavGroup[] = [
  {
    id: "comunidade",
    label: "Comunidade",
    items: [
      { href: "/feed", label: "Início", icon: I("inicio") },
      { href: "/feed?kind=CASE", label: "Casos clínicos", icon: I("casos-clinicos") },
      { href: "/feed?kind=OPINION_REQUEST", label: "Pedidos de parecer", icon: I("mensagens") },
      { href: "/feed?kind=EDUCATION", label: "Educação", icon: I("grupos") },
      { href: "/feed?kind=PROTOCOL", label: "Protocolos", icon: I("prontuario") },
      { href: "/feed?kind=NOTICE", label: "Comunicados", icon: I("instituicoes") },
      { href: "/casos/novo", label: "Publicar caso", icon: NEW },
      { href: "/grupos", label: "Grupos", icon: I("grupos") },
    ],
  },
  {
    id: "mensagens",
    label: "Mensagens",
    items: [
      { href: "/mensagens", label: "Conversas", icon: I("mensagens"), also: ["/chamadas"] },
      { href: "/mensagens/aparelhos", label: "Aparelhos e chaves", icon: "/icons/topbar/e2ee.svg" },
    ],
  },
  {
    id: "prontuario",
    label: "Prontuário",
    items: [
      { href: "/prontuario", label: "Pacientes", icon: I("prontuario") },
      { href: "/prontuario/nova", label: "Nova nota", icon: NEW },
      { href: "/prescricoes", label: "Receitas e pedidos", icon: I("prescricao"), also: ["/prontuario/documentos"] },
      { href: "/exames", label: "Exames", icon: I("exames"), soon: true },
    ],
  },
  {
    id: "instituicoes",
    label: "Instituições",
    items: [
      { href: "/instituicoes", label: "Diretório", icon: I("instituicoes") },
      { href: "/instituicoes/nova", label: "Cadastrar instituição", icon: NEW },
    ],
  },
  { id: "moderacao", label: "Moderação", mod: true, items: [{ href: "/moderacao", label: "Fila de moderação", icon: I("administracao") }] },
  {
    id: "painel",
    label: "Painel da plataforma",
    panel: true,
    items: [
      { href: "/admin", label: "Engajamento", icon: I("inicio") },
      { href: "/admin?aba=auditoria", label: "Auditoria", icon: I("administracao") },
      { href: "/admin?aba=qualidade", label: "Qualidade", icon: I("configuracoes") },
    ],
  },
  {
    id: "conta",
    label: "Conta",
    items: [
      { href: "/perfil", label: "Meu perfil", icon: "/icons/topbar/perfil.svg", exact: true },
      { href: "/privacidade", label: "Privacidade", icon: I("privacidade") },
      { href: "/seguranca", label: "Sessões e segurança", icon: "/icons/topbar/e2ee.svg" },
      { href: "/aparencia", label: "Aparência", icon: I("configuracoes") },
    ],
  },
];

export function visibleNav(roles: string[]): NavGroup[] {
  const moderator = roles.some((r) => r === "MODERATOR" || r === "ADMIN");
  const panel = roles.some((r) => r === "MODERATOR" || r === "ADMIN" || r === "DPO");
  return NAV.filter((g) => (!g.mod || moderator) && (!g.panel || panel));
}

const underPath = (pathname: string, path: string, exact?: boolean) => pathname === path || (!exact && pathname.startsWith(path + "/"));

/**
 * O item que representa a página atual: vence o caminho mais longo e, empatado, o que exige mais parâmetros da URL
 * (em /feed?kind=CASE acende "Casos clínicos", não "Início"; em /admin?aba=auditoria, "Auditoria").
 */
export function activeHref(groups: NavGroup[], pathname: string, search: URLSearchParams): string | null {
  let best: { href: string; score: number } | null = null;
  for (const item of groups.flatMap((g) => g.items)) {
    if (item.soon) continue;
    const [path = "", query = ""] = item.href.split("?");
    const wanted = [...new URLSearchParams(query)];
    if (!wanted.every(([k, v]) => search.get(k) === v)) continue;
    const paths = [path, ...(item.also ?? [])].filter((p) => underPath(pathname, p, item.exact));
    if (!paths.length) continue;
    const score = Math.max(...paths.map((p) => p.length)) * 10 + wanted.length;
    if (!best || score > best.score) best = { href: item.href, score };
  }
  return best?.href ?? null;
}
