import {spinKey, type Spins} from "../layout/calculation";
import {Observable} from "./observable";
import {ALL, ORBITAL_KINDS, type OrbitalKind, type OrbitalScope, sameScope} from "./OrbitalModel";

const CALCULATION_KEY = "studio.calculation";

export interface CalculationState {
  kind: OrbitalKind;
  scope: OrbitalScope;
  spins: Spins;
  /** How many times a fresh calculation has been asked for, which makes everything that is shown ask again. */
  run: number;
}

/**
 * How the session is calculated. Only the kind of calculation is remembered between visits: what is calculated and at what spin belong to one structure.
 */
export class CalculationModel extends Observable<CalculationState> {
  constructor(kind: OrbitalKind = CalculationModel.read()) {
    super({ kind, scope: ALL, spins: {}, run: 0 });
  }

  /**
   * Changes the kind of calculation. Closed-shell Hartree-Fock is a singlet, so any other spin chosen is put away when it is chosen.
   *
   * @param kind the kind of calculation
   */
  kindTo(kind: OrbitalKind): void {
    const state = this.get();
    if (state.kind === kind) {
      return;
    }

    const spins = kind === "rhf" ? Object.fromEntries(Object.entries(state.spins).filter(([, multiplicity]) => multiplicity === 1)) : state.spins;
    this.set({ ...state, kind, spins });
    try {
      localStorage.setItem(CALCULATION_KEY, JSON.stringify({ kind }));
    } catch {
      // a browser that refuses storage still gets the setting, just not remembered
    }
  }

  /**
   * @param scope what to calculate: everything together, each molecule, or one
   */
  scopeTo(scope: OrbitalScope): void {
    if (!sameScope(this.get().scope, scope)) {
      this.set({ ...this.get(), scope });
    }
  }

  /**
   * Chooses the spin of what is calculated.
   *
   * @param scope what the spin is for, whose whole session or molecule has the spin; a calculation of each molecule has no spin of its own
   * @param multiplicity the spin multiplicity, or null for the lowest the electron count allows
   */
  spinTo(scope: OrbitalScope, multiplicity: number | null): void {
    if (scope.kind === "each") {
      return;
    }

    const state = this.get();
    const key = spinKey(scope);
    if ((state.spins[key] ?? null) === multiplicity) {
      return;
    }
    const spins = { ...state.spins };
    if (multiplicity === null) {
      delete spins[key];
    } else {
      spins[key] = multiplicity;
    }
    this.set({ ...state, spins });
  }

  /** Asks for everything that is shown to be calculated afresh. */
  recalculate(): void {
    this.set({ ...this.get(), run: this.get().run + 1 });
  }

  private static read(): OrbitalKind {
    try {
      const stored = JSON.parse(localStorage.getItem(CALCULATION_KEY) ?? "null") as { kind?: unknown } | null;
      return ORBITAL_KINDS.find((each) => each.value === stored?.kind)?.value ?? "mulliken";
    } catch {
      return "mulliken";
    }
  }
}
