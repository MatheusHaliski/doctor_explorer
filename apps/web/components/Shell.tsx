"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, useSyncExternalStore, type ReactNode } from "react";
import { api } from "@/lib/api";
import { isDark, readCachedAppearance, setAppearance, subscribeAppearance } from "@/lib/appearance";
import { Seal } from "./Seal";

type Props = { user: { userId: string; displayName: string; status: string; roles?: string[] }; seal: string; children: ReactNode };

/* eslint-disable @next/next/no-img-element */
const NAV: Array<{ sep?: string; href?: string; label?: string; icon?: string; soon?: boolean; mod?: boolean }> = [
  { href: "/feed", label: "Início", icon: "/icons/nav/inicio.svg" },
  { href: "/feed?kind=CASE", label: "Casos clínicos", icon: "/icons/nav/casos-clinicos.svg" },
  { href: "/grupos", label: "Grupos", icon: "/icons/nav/grupos.svg" },
  { href: "/mensagens", label: "Mensagens", icon: "/icons/nav/mensagens.svg", soon: true },
  { href: "/chamadas", label: "Chamadas", icon: "/icons/nav/chamadas.svg", soon: true },
  { sep: "Clínico" },
  { href: "/prontuario", label: "Prontuário", icon: "/icons/nav/prontuario.svg", soon: true },
  { href: "/prescricoes", label: "Prescrições", icon: "/icons/nav/prescricao.svg", soon: true },
  { href: "/exames", label: "Exames", icon: "/icons/nav/exames.svg", soon: true },
  { sep: "Instituição" },
  { href: "/instituicoes", label: "Instituições", icon: "/icons/nav/instituicoes.svg" },
  { sep: "Moderação", mod: true },
  { href: "/moderacao", label: "Fila de moderação", icon: "/icons/nav/administracao.svg", mod: true },
  { sep: "Conta" },
  { href: "/perfil", label: "Perfil", icon: "/icons/topbar/perfil.svg" },
  { href: "/privacidade", label: "Privacidade", icon: "/icons/nav/privacidade.svg" },
  { href: "/seguranca", label: "Sessões e segurança", icon: "/icons/topbar/e2ee.svg" },
  { href: "/aparencia", label: "Aparência", icon: "/icons/nav/configuracoes.svg" },
];

export function Shell({ user, seal, children }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const dark = useSyncExternalStore(subscribeAppearance, isDark, () => false);
  const toggleTheme = () => {
    const next = { ...readCachedAppearance(), theme: dark ? ("light" as const) : ("dark" as const) };
    setAppearance(next);
    api.put("/api/v1/me/preferences", { theme: next.theme }).catch(() => {}); // a troca já valeu na tela; a sincronização tenta de novo na próxima mudança
  };
  const close = () => setOpen(false);

  async function logout() {
    try {
      await api.post("/api/v1/auth/logout");
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  const moderator = (user.roles ?? []).some((r) => r === "MODERATOR" || r === "ADMIN");
  const nav = NAV.filter((n) => !n.mod || moderator);
  const current = (href: string) => (href === "/feed" ? pathname === "/feed" : pathname.startsWith(href.split("?")[0]!));

  return (
    <>
      <a className="skip" href="#main">
        Pular para o conteúdo
      </a>
      <div className="app">
        {open && <button type="button" className="scrim only-mobile" aria-label="Fechar menu" onClick={() => setOpen(false)} />}
        <aside className={`sidebar ${open ? "open" : ""}`} aria-label="Navegação principal">
          <Link className="brand" href="/feed">
            <img src="/brand/logo-mark.svg" alt="" />
            Doctor Explorer
          </Link>
          <nav className="nav">
            {nav.map((n, i) =>
              n.sep ? (
                <div className="sep" key={i}>
                  {n.sep}
                </div>
              ) : n.soon ? (
                <a key={n.href} href="#" className="soon" aria-disabled="true" title="Em breve" onClick={(e) => e.preventDefault()}>
                  <img src={n.icon} alt="" />
                  {n.label}
                  <span className="ml-auto text-xs text-text-3">em breve</span>
                </a>
              ) : (
                <Link key={n.href} href={n.href!} aria-current={current(n.href!) ? "page" : undefined} onClick={close}>
                  <img src={n.icon} alt="" />
                  {n.label}
                </Link>
              ),
            )}
          </nav>
        </aside>
        <div className="min-w-0">
          <header className="topbar">
            <button className="ib only-mobile" type="button" aria-label="Abrir menu" aria-expanded={open} onClick={() => setOpen(true)}>
              <img src="/icons/topbar/menu.svg" alt="" />
            </button>
            <form
              className="search"
              role="search"
              onSubmit={(e) => {
                e.preventDefault();
                const q = new FormData(e.currentTarget).get("q")?.toString().trim() ?? "";
                router.push(q ? `/instituicoes?q=${encodeURIComponent(q)}` : "/instituicoes");
              }}
            >
              <img src="/icons/topbar/buscar.svg" alt="" />
              <label className="sr-only" htmlFor="q">
                Buscar instituições
              </label>
              <input id="q" name="q" type="search" placeholder="Buscar instituições" />
            </form>
            <span className="grow" />
            <Seal status={seal} />
            <button className="ib" type="button" aria-label="Alternar tema escuro" aria-pressed={dark} onClick={toggleTheme}>
              <img src="/icons/nav/configuracoes.svg" alt="" />
            </button>
            <Link className="ib" href="/perfil" aria-label={`Perfil de ${user.displayName}`}>
              <img src="/icons/topbar/perfil.svg" alt="" />
            </Link>
            <button className="ib" type="button" aria-label="Sair" onClick={logout}>
              <img src="/icons/topbar/sair.svg" alt="" />
            </button>
          </header>
          <main id="main" tabIndex={-1}>
            {children}
          </main>
        </div>
      </div>
    </>
  );
}
