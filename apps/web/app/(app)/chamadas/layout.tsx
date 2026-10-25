import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "Chamada" };

export default function CallsLayout({ children }: { children: ReactNode }) {
  return children;
}
