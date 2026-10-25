"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { api } from "@/lib/api";
import { isDark, readCachedAppearance, setAppearance, subscribeAppearance } from "@/lib/appearance";
import { activeHref, visibleNav } from "@/lib/nav";
import { Seal } from "./Seal";
import { IncomingCall } from "./call/IncomingCall";

type Props = { user: { userId: string; displayName: string; status: string; roles?: string[] }; seal: string; children: ReactNode };

/* eslint-disable @next/next/no-img-element */
export function Shell({ user, seal, children }: Props) {
  const pathname = usePathname();
  const search = useSearchParams();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const dark = useSyncExternalStore(subscribeAppearance, isDark, () => false);
  const toggleTheme = () => {
    const next = { ...readCachedAppearance(), theme: dark ? ("light" as const) : ("dark" as const) };
    setAppearance(next);
    api.put("/api/v1/me/preferences", { theme: next.theme }).catch(() => {}); // a troca já valeu na tela; a sincronização tenta de novo na próxima mudança
  };
  const close = () => setOpen(false);
  const unread = useUnread();

  async function logout() {
    try {
      await api.post("/api/v1/auth/logout");
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  const groups = visibleNav(user.roles ?? []);
  const active = activeHref(groups, pathname, search);

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
            {groups.map((g) => (
              <div key={g.id} className="nav-group" role="group" aria-labelledby={`nav-${g.id}`}>
                <p className="nav-label" id={`nav-${g.id}`}>
                  {g.label}
                </p>
                <ul>
                  {g.items.map((n) => (
                    <li key={n.href}>
                      {n.soon ? (
                        <a href="#" className="soon" aria-disabled="true" title="Em breve" onClick={(e) => e.preventDefault()}>
                          <img src={n.icon} alt="" />
                          {n.label}
                          <span className="ml-auto text-xs text-text-3">em breve</span>
                        </a>
                      ) : (
                        <Link href={n.href} aria-current={n.href === active ? "page" : undefined} onClick={close}>
                          <img src={n.icon} alt="" />
                          {n.label}
                          {n.href === "/mensagens" && unread > 0 && (
                            <span className="badge ml-auto" aria-label={`${unread} ${unread === 1 ? "conversa com mensagem nova" : "conversas com mensagens novas"}`}>
                              {unread}
                            </span>
                          )}
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
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
            <IncomingCall />
            {children}
          </main>
        </div>
      </div>
    </>
  );
}

/** RF06 critério 9 · aviso de mensagem nova sem conteúdo: só a contagem de conversas com novidade. */
function useUnread(): number {
  const [n, setN] = useState(0);
  useEffect(() => {
    let alive = true;
    const load = () =>
      api
        .get<{ conversations: number }>("/api/v1/messaging/unread")
        .then((r) => alive && setN(r.conversations))
        .catch(() => {});
    void load();
    const t = setInterval(load, 30_000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, []);
  return n;
}
