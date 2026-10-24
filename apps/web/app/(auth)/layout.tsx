import Link from "next/link";
import type { ReactNode } from "react";

/* eslint-disable @next/next/no-img-element */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <div className="card">
        <Link className="brand" href="/">
          <img src="/brand/logo-mark.svg" alt="" />
          Doctor Explorer
        </Link>
        {children}
        <p className="mt-6 text-center text-sm text-text-3">
          Sessão opaca em cookie HttpOnly · nada fica guardado neste navegador além do tema.
        </p>
      </div>
    </div>
  );
}
