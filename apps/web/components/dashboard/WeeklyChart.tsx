"use client";

import { useState } from "react";

export type Cell = { value: number | null; suppressed: boolean };
export type Week = { week: string; weekStart: string; cases: Cell; opinions: Cell };

const SERIES: Array<{ key: "cases" | "opinions"; label: string; color: string }> = [
  { key: "cases", label: "Casos publicados", color: "var(--series-1)" },
  { key: "opinions", label: "Pareceres", color: "var(--series-2)" },
];

/** Escala com números redondos (0, 2, 4…; 0, 5, 10…) para os rótulos do eixo. */
function niceMax(v: number): { max: number; step: number } {
  if (v <= 4) return { max: 4, step: 1 };
  const raw = v / 4;
  const pow = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow;
  return { max: Math.ceil(v / step) * step, step };
}

function fmtWeek(iso: string) {
  return new Date(iso + "T00:00:00Z").toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", timeZone: "UTC" });
}

/**
 * RF13 · casos e pareceres por semana (colunas agrupadas, um eixo só). Semana com menos de 10 pessoas não vira
 * coluna: aparece "<10" e a tabela diz "suprimido". Legenda sempre visível, dica no hover e no foco, tabela alternativa.
 */
export function WeeklyChart({ weeks }: { weeks: Week[] }) {
  const [tip, setTip] = useState<{ x: number; y: number; w: Week } | null>(null);
  const [asTable, setAsTable] = useState(false);
  const values = weeks.flatMap((w) => [w.cases.value ?? 0, w.opinions.value ?? 0]);
  const { max, step } = niceMax(Math.max(1, ...values));
  const W = 720, H = 260, left = 40, right = 8, top = 12, bottom = 34;
  const plotW = W - left - right, plotH = H - top - bottom;
  const band = plotW / Math.max(1, weeks.length);
  const barW = Math.min(24, (band - 16) / 2);
  const y = (v: number) => top + plotH - (v / max) * plotH;
  const ticks = Array.from({ length: Math.round(max / step) + 1 }, (_, i) => i * step);

  /** Coluna com topo arredondado (4px) e base reta. */
  const bar = (x: number, v: number) => {
    const yy = y(v), h = top + plotH - yy, r = Math.min(4, h, barW / 2);
    return `M${x},${top + plotH} V${yy + r} Q${x},${yy} ${x + r},${yy} H${x + barW - r} Q${x + barW},${yy} ${x + barW},${yy + r} V${top + plotH} Z`;
  };

  return (
    <figure className="m-0">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="legend" aria-hidden="true">
          {SERIES.map((s) => (
            <span key={s.key}>
              <span className="sw" style={{ background: s.color }} />
              {s.label}
            </span>
          ))}
          <span>&lt;10 = menos de 10 pessoas (suprimido)</span>
        </div>
        <button type="button" className="btn sm" aria-pressed={asTable} onClick={() => setAsTable(!asTable)}>
          {asTable ? "Ver gráfico" : "Ver como tabela"}
        </button>
      </div>
      {asTable ? (
        <table className="data">
          <caption className="sr-only">Casos publicados e pareceres por semana</caption>
          <thead>
            <tr>
              <th>Semana (início)</th>
              <th className="num">Casos publicados</th>
              <th className="num">Pareceres</th>
            </tr>
          </thead>
          <tbody>
            {weeks.map((w) => (
              <tr key={w.week}>
                <td>
                  {w.week} · {fmtWeek(w.weekStart)}
                </td>
                <td className="num">{w.cases.suppressed ? "suprimido" : w.cases.value}</td>
                <td className="num">{w.opinions.suppressed ? "suprimido" : w.opinions.value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="chart" onPointerLeave={() => setTip(null)}>
          <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Casos publicados e pareceres por semana, últimas 8 semanas. Use “Ver como tabela” para os valores.">
            {ticks.map((t) => (
              <g key={t}>
                <line x1={left} x2={W - right} y1={y(t)} y2={y(t)} stroke="var(--grid)" strokeWidth={1} />
                <text x={left - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill="var(--text-3)">
                  {t}
                </text>
              </g>
            ))}
            {weeks.map((w, i) => {
              const gx = left + i * band + (band - (barW * 2 + 2)) / 2;
              const show = (e: React.PointerEvent | React.FocusEvent) => {
                const target = e.currentTarget as SVGGElement;
                const svg = target.ownerSVGElement!.getBoundingClientRect();
                const box = target.getBoundingClientRect();
                setTip({ x: box.left - svg.left + box.width / 2, y: box.top - svg.top, w });
              };
              return (
                <g key={w.week} className="bar" tabIndex={0} role="img" aria-label={`Semana ${w.week}: ${w.cases.suppressed ? "casos suprimidos" : `${w.cases.value} casos`}, ${w.opinions.suppressed ? "pareceres suprimidos" : `${w.opinions.value} pareceres`}`}
                  onPointerMove={show} onFocus={show} onBlur={() => setTip(null)}>
                  {/* área de toque maior que a marca */}
                  <rect x={left + i * band} y={top} width={band} height={plotH} fill="transparent" />
                  {SERIES.map((s, k) => {
                    const cell = w[s.key];
                    const x = gx + k * (barW + 2);
                    return cell.suppressed || cell.value === null ? (
                      <text key={s.key} x={x + barW / 2} y={top + plotH - 6} textAnchor="middle" fontSize="10" fill="var(--text-3)">
                        &lt;10
                      </text>
                    ) : cell.value > 0 ? (
                      <path key={s.key} d={bar(x, cell.value)} fill={s.color} />
                    ) : null;
                  })}
                  <text x={left + i * band + band / 2} y={H - 12} textAnchor="middle" fontSize="11" fill="var(--text-2)">
                    {fmtWeek(w.weekStart)}
                  </text>
                </g>
              );
            })}
            <line x1={left} x2={W - right} y1={top + plotH} y2={top + plotH} stroke="var(--line)" strokeWidth={1} />
          </svg>
          {tip && (
            <div className="tip" style={{ left: Math.min(tip.x + 8, 560), top: Math.max(0, tip.y - 64) }} role="status">
              <div className="text-xs text-text-3">
                Semana de {fmtWeek(tip.w.weekStart)} ({tip.w.week})
              </div>
              {SERIES.map((s) => (
                <div key={s.key} className="row">
                  <span className="key" style={{ background: s.color }} />
                  <strong>{tip.w[s.key].suppressed ? "<10 pessoas" : tip.w[s.key].value}</strong>
                  <span className="text-text-2">{s.label.toLowerCase()}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
      <figcaption className="mt-2 text-xs text-text-3">Cada ponto só aparece quando formado por 10 pessoas ou mais (k-anonimato). Sem dado de saúde, sem detalhamento por pessoa.</figcaption>
    </figure>
  );
}
