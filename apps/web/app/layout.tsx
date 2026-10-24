import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { APPEARANCE_BOOT_SCRIPT } from "@/lib/appearance";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Doctor Explorer", template: "%s · Doctor Explorer" },
  description: "Rede profissional médica e hospitalar com verificação de CRM, discussão de casos anonimizados e privacidade por padrão.",
  icons: { icon: "/brand/logo-mark.svg" },
  robots: { index: false }, // conteúdo clínico nunca indexado
};

export const viewport: Viewport = { width: "device-width", initialScale: 1, colorScheme: "light dark" };

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: APPEARANCE_BOOT_SCRIPT }} />
      </head>
      <body>
        {/* arte de fundo escolhida em Aparência; decorativa, fica atrás de tudo */}
        <div className="app-bg" aria-hidden="true" />
        {children}
      </body>
    </html>
  );
}
