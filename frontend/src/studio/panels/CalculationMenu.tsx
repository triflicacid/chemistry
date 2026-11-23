import {useCallback, useEffect, useRef, useState} from "react";
import {calculationLabel, describeRun, type Electrons, electronsFor, spinChoices, spinOf} from "../layout/calculation";
import type {CalculationModel} from "../models/CalculationModel";
import {ORBITAL_KINDS, type OrbitalKind, type OrbitalScope} from "../models/OrbitalModel";
import {useObservable} from "../models/observable";
import type {SessionState} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";

/** The values of the choice of what is calculated, which a dropdown holds as text. */
const EACH = "each";
const ONE = "one:";

/**
 * @param scope what is calculated
 * @returns the text a dropdown holds for it
 */
function valueOf(scope: OrbitalScope): string {
  return scope.kind === "all" ? "" : scope.kind === "each" ? EACH : `${ONE}${scope.atom}`;
}

/**
 * @param value the text a dropdown holds
 * @returns what is calculated
 */
function scopeOf(value: string): OrbitalScope {
  return value === "" ? { kind: "all" } : value === EACH ? { kind: "each" } : { kind: "one", atom: value.slice(ONE.length) };
}

export interface CalculationMenuProps {
  /** How the electrons of the session are calculated. */
  calculation: CalculationModel;
  /** The session, whose molecules can be calculated on their own, or null if there is none. */
  state: SessionState | null;
  /** Whether there is a session to calculate. */
  enabled: boolean;
}

/** Hartree-Fock method etc. */
export function CalculationMenu({ calculation, state, enabled }: CalculationMenuProps) {
  const { kind, scope, spins } = useObservable(calculation);
  const { lastCalculation, requestElectrons, resetCalculations } = useSessionContext();
  const [open, setOpen] = useState(false);
  const [electrons, setElectrons] = useState<Electrons | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const close = useCallback(() => setOpen(false), []);
  const version = state?.version;

  useEffect(() => {
    if (!open) {
      return;
    }

    const away = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) {
        close();
      }
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        close();
      }
    };
    document.addEventListener("pointerdown", away);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", away);
      document.removeEventListener("keydown", escape);
    };
  }, [open, close]);

  // which spins the electrons allow depends on how many there are, which changes as atoms are edited, so it is asked for while the menu is open
  useEffect(() => {
    if (!open || version === undefined) {
      return;
    }
    let current = true;
    void requestElectrons().then((answer) => {
      if (current) {
        setElectrons(answer);
      }
    });
    return () => {
      current = false;
    };
  }, [open, version, requestElectrons]);

  const fragments = state?.fragments ?? [];
  const nameOf = (atom: string) => {
    const found = fragments.find((each) => each.atoms[0] === atom);
    return found ? found.formula.molecular : null;
  };
  const known = electronsFor(scope, electrons);
  const choices = spinChoices(known, kind);
  const spin = spinOf(spins, scope);
  const label = calculationLabel(kind);
  const tailored = scope.kind === "each";
  // a calculation of one molecule on its own is named, so that it is not taken for the whole
  const lastOf = lastCalculation?.fragment ? nameOf(lastCalculation.fragment) : null;

  const recalculate = async () => {
    close();
    if (await resetCalculations()) {
      calculation.recalculate();
    }
  };

  return (
    <div className="calculation" ref={root}>
      <button
        type="button"
        className="tool"
        disabled={!enabled}
        aria-haspopup="true"
        aria-expanded={open}
        title="What the electrons are calculated with: of what, by which kind of Hartree-Fock, and at what spin. Charges, orbitals, surfaces and slices all use it"
        onClick={() => setOpen(!open)}
      >
        {label}
      </button>
      {open && (
        <div className="calculation-menu" role="dialog" aria-label="Calculation">
          <dl className="properties">
            <dt>Of</dt>
            <dd>
              <select value={valueOf(scope)} aria-label="What is calculated" onChange={(event) => calculation.scopeTo(scopeOf(event.target.value))}>
                <option value="">All molecules together</option>
                {fragments.length > 1 && (
                  <option value={EACH} title="The orbitals of each molecule on its own, which the Orbitals tab shows; everything else is of the whole session">
                    Each molecule
                  </option>
                )}
                {fragments.map((each, i) => (
                  <option key={each.key} value={`${ONE}${each.atoms[0] ?? ""}`}>
                    {`${i + 1}. ${each.name} (${each.formula.molecular})`}
                  </option>
                ))}
              </select>
            </dd>
            <dt>Method</dt>
            <dd>
              <select value={kind} aria-label="The kind of Hartree-Fock calculation" onChange={(event) => calculation.kindTo(event.target.value as OrbitalKind)}>
                {ORBITAL_KINDS.map(({ value, label: name, title }) => (
                  <option key={value} value={value} title={title}>
                    {name}
                  </option>
                ))}
              </select>
            </dd>
            <dt>Spin</dt>
            <dd>
              {tailored ? (
                <span className="legend-note">Each at its own spin: choose a molecule above to set it</span>
              ) : (
                <>
                  <select
                    value={spin === null ? "" : String(spin)}
                    aria-label="The spin multiplicity"
                    onChange={(event) => calculation.spinTo(scope, event.target.value === "" ? null : Number(event.target.value))}
                  >
                    <option value="" title="The lowest the electrons allow: a singlet where they pair up, otherwise a doublet">
                      Automatic
                    </option>
                    {choices.map((choice) => (
                      <option key={choice.multiplicity} value={String(choice.multiplicity)} disabled={choice.disabled} title={choice.reason ?? undefined}>
                        {choice.label}
                      </option>
                    ))}
                  </select>
                  {known !== null && <span className="unit"> {known} electrons</span>}
                </>
              )}
            </dd>
            <dt>Basis</dt>
            <dd className="legend-note">CEP-31G with core potentials</dd>
          </dl>
          <div className="calculation-last" role="status">
            {lastCalculation ? `Last${lastOf ? ` (${lastOf})` : ""}: ${describeRun(lastCalculation)}` : "Nothing calculated yet"}
          </div>
          <div className="calculation-actions">
            <button type="button" className="tool" disabled={state === null} title="Forget what has been calculated and calculate it all again" onClick={() => void recalculate()}>
              Recalculate
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
