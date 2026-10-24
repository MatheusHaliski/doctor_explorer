"use client";

import { useEffect, useState, type ReactNode } from "react";
import { ApiError, describeError } from "@/lib/api";

/** Aviso inline. `tone` segue os tokens semânticos; o ícone é só decorativo (o texto diz tudo). */
export function Notice({ tone = "warning", children, role }: { tone?: "warning" | "danger" | "success" | "info"; children: ReactNode; role?: "alert" | "status" }) {
  return (
    <div className={`notice ${tone}`} role={role ?? (tone === "danger" ? "alert" : "status")}>
      <span>{children}</span>
    </div>
  );
}

/** Mostra um erro da API: título + detalhe, e a lista de campos quando for 422. */
export function ProblemAlert({ error }: { error: unknown }) {
  if (!error) return null;
  const p = error instanceof ApiError ? error.problem : null;
  return (
    <Notice tone="danger" role="alert">
      <strong>{p?.title ?? "Algo deu errado"}.</strong> {describeError(error)}
      {p?.errors && p.errors.length > 0 && (
        <ul className="mt-2 list-disc pl-5">
          {p.errors.map((e, i) => (
            <li key={i}>
              <span className="font-bold">{e.field}</span>: {e.message}
            </li>
          ))}
        </ul>
      )}
    </Notice>
  );
}

type FieldProps = {
  id: string;
  label: string;
  help?: string;
  errors?: string[];
  children: (props: { id: string; "aria-describedby"?: string; "aria-invalid"?: boolean }) => ReactNode;
};

/** Campo com rótulo visível, ajuda e erros ligados por aria-describedby (heurísticas 5 e 9). */
export function Field({ id, label, help, errors = [], children }: FieldProps) {
  const ids = [help ? `${id}-help` : null, errors.length ? `${id}-err` : null].filter(Boolean).join(" ") || undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children({ id, "aria-describedby": ids, "aria-invalid": errors.length > 0 || undefined })}
      {help && (
        <span className="help" id={`${id}-help`}>
          {help}
        </span>
      )}
      {errors.length > 0 && (
        <span className="err" id={`${id}-err`} role="alert">
          {errors.join(" ")}
        </span>
      )}
    </div>
  );
}

export function Button({ children, variant, size, loading, className = "", ...rest }: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "danger"; size?: "sm"; loading?: boolean }) {
  return (
    <button {...rest} disabled={rest.disabled || loading} className={`btn ${variant ?? ""} ${size ?? ""} ${className}`} aria-busy={loading || undefined}>
      {loading ? "Aguarde…" : children}
    </button>
  );
}

/** Toast simples com desfazer opcional; some em 6 s. */
export function useToast() {
  const [toast, setToast] = useState<{ text: string; undo?: () => void } | null>(null);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(t);
  }, [toast]);
  const el = toast ? (
    <div className="toast" role="status">
      {toast.text}
      {toast.undo && (
        <button
          type="button"
          onClick={() => {
            toast.undo?.();
            setToast(null);
          }}
        >
          Desfazer
        </button>
      )}
    </div>
  ) : null;
  return { show: (text: string, undo?: () => void) => setToast({ text, undo }), el };
}

export function Loading({ what = "Carregando" }: { what?: string }) {
  return (
    <p className="result-line" role="status">
      {what}…
    </p>
  );
}
