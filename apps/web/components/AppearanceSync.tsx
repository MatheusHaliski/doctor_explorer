"use client";

import { useEffect } from "react";
import { setAppearance, type Appearance } from "@/lib/appearance";

/** Aplica as preferências vindas do servidor (fonte da verdade) e atualiza a cópia local do aparelho. */
export function AppearanceSync({ value }: { value: Appearance }) {
  useEffect(() => {
    setAppearance(value);
  }, [value]);
  return null;
}
