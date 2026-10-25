import Link from "next/link";
import type { ReactNode } from "react";

/* eslint-disable @next/next/no-img-element */
/** Páginas sem login (farmácia e paciente): sem menu, sem sessão, nada guardado no navegador. */
export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth public">
      <div className="card">
        <Link className="brand" href="/">
          <img src="/brand/logo-mark.svg" alt="" />
          Doctor Explorer
        </Link>
        {children}
      </div>
    </div>
  );
}
