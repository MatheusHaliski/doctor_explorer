"use client";

import { ROUTES, UNITS, type MedicationInput } from "@/lib/documents";
import { Button, Field } from "@/components/ui";

type Errors = (field: string) => string[];

/** Posologia estruturada de um medicamento: dose, via, frequência (intervalo ou vezes ao dia), duração. */
export function MedicationItem({
  index,
  value,
  label,
  antimicrobial,
  errors,
  onChange,
  onRemove,
}: {
  index: number;
  value: MedicationInput;
  label: string;
  antimicrobial: boolean;
  errors: Errors;
  onChange: (m: MedicationInput) => void;
  onRemove: () => void;
}) {
  const f = (name: string) => errors(`medications[${index}].${name}`);
  const id = (name: string) => `med-${index}-${name}`;
  const byInterval = value.timesPerDay === null;
  const set = (patch: Partial<MedicationInput>) => onChange({ ...value, ...patch });
  const num = (v: string) => (v === "" ? null : Number(v));

  return (
    <li className="card doc-item" aria-label={label}>
      <div className="doc-item-head">
        <strong>
          {index + 1}. {label}
        </strong>
        <Button type="button" size="sm" onClick={onRemove}>
          Remover
        </Button>
      </div>
      {f("ggrem").map((m) => (
        <p key={m} className="field-error" role="alert">
          {m}
        </p>
      ))}
      <div className="doc-grid">
        <Field id={id("dose")} label="Dose" errors={f("dose")} help="1, 0,5 ou 1/2">
          {(p) => <input {...p} className="input" inputMode="decimal" value={value.dose} onChange={(e) => set({ dose: e.target.value })} />}
        </Field>
        <Field id={id("unit")} label="Unidade" errors={f("doseUnit")}>
          {(p) => (
            <select {...p} value={value.doseUnit} onChange={(e) => set({ doseUnit: e.target.value })}>
              {UNITS.map((u) => (
                <option key={u} value={u}>
                  {u}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field id={id("route")} label="Via" errors={f("route")}>
          {(p) => (
            <select {...p} value={value.route} onChange={(e) => set({ route: e.target.value })}>
              {Object.entries(ROUTES).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          )}
        </Field>
      </div>
      <fieldset className="doc-fieldset">
        <legend>Frequência</legend>
        <div className="segmented" role="radiogroup" aria-label="Frequência">
          <label>
            <input type="radio" name={id("freq")} checked={byInterval} onChange={() => set({ intervalHours: value.intervalHours ?? 8, timesPerDay: null })} />
            De tantas em tantas horas
          </label>
          <label>
            <input type="radio" name={id("freq")} checked={!byInterval} onChange={() => set({ timesPerDay: value.timesPerDay ?? 1, intervalHours: null })} />
            Vezes ao dia
          </label>
        </div>
        {byInterval ? (
          <Field id={id("interval")} label="Intervalo (horas)" errors={[...f("intervalHours"), ...f("frequency")]}>
            {(p) => <input {...p} className="input" type="number" min={1} max={168} value={value.intervalHours ?? ""} onChange={(e) => set({ intervalHours: num(e.target.value) })} />}
          </Field>
        ) : (
          <Field id={id("times")} label="Vezes ao dia" errors={[...f("timesPerDay"), ...f("frequency")]}>
            {(p) => <input {...p} className="input" type="number" min={1} max={24} value={value.timesPerDay ?? ""} onChange={(e) => set({ timesPerDay: num(e.target.value) })} />}
          </Field>
        )}
      </fieldset>
      <div className="doc-grid">
        <Field id={id("days")} label="Duração (dias)" errors={[...f("durationDays"), ...f("continuous")]} help={antimicrobial ? "Antimicrobiano: duração obrigatória." : undefined}>
          {(p) => (
            <input
              {...p}
              className="input"
              type="number"
              min={1}
              max={365}
              disabled={value.continuous}
              value={value.continuous ? "" : (value.durationDays ?? "")}
              onChange={(e) => set({ durationDays: num(e.target.value) })}
            />
          )}
        </Field>
        <Field id={id("qty")} label="Quantidade (opcional)" errors={f("quantity")} help="Ex.: 1 caixa">
          {(p) => <input {...p} className="input" maxLength={60} value={value.quantity ?? ""} onChange={(e) => set({ quantity: e.target.value || null })} />}
        </Field>
      </div>
      {!antimicrobial && (
        <label className="switch">
          <input type="checkbox" checked={value.continuous} onChange={(e) => set({ continuous: e.target.checked, durationDays: e.target.checked ? null : (value.durationDays ?? 30) })} />
          Uso contínuo
        </label>
      )}
      <Field id={id("instr")} label="Orientações (opcional)" errors={f("instructions")} help="Ex.: tomar após as refeições. Até 300 caracteres.">
        {(p) => <input {...p} className="input" maxLength={300} value={value.instructions ?? ""} onChange={(e) => set({ instructions: e.target.value || null })} />}
      </Field>
    </li>
  );
}
