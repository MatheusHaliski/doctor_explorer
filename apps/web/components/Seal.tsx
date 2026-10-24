import { SEALS } from "@/lib/domain";

/** Selo profissional: sempre cor + ícone + texto (RF02, critério 7). */
export function Seal({ status, className = "" }: { status: string; className?: string }) {
  const s = SEALS[status] ?? SEALS.NONE;
  return (
    <span className={`seal ${s.cls} ${className}`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={s.icon} alt="" />
      {s.label}
    </span>
  );
}
