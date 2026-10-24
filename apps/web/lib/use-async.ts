import { useEffect, type DependencyList } from "react";

/**
 * Efeito assíncrono: roda `run` (que faz as chamadas à API e atualiza o estado) e entrega o erro a `onError`.
 * Centraliza o padrão "carregar ao montar / ao mudar a URL" para as telas não repetirem o try/catch.
 */
export function useAsyncEffect(run: () => Promise<unknown>, onError: (e: unknown) => void, deps: DependencyList) {
  useEffect(() => {
    let alive = true;
    run().catch((e) => {
      if (alive) onError(e);
    });
    return () => {
      alive = false;
    };
    // `run` é recriado a cada render; as dependências reais são as de `deps`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
