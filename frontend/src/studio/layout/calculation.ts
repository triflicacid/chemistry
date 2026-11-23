import type {OrbitalKind, OrbitalScope} from "../models/OrbitalModel";

/** The spin multiplicity to calculate at, by the molecule it is for: an atom of it, or the empty string for the whole session. Absent means the lowest. */
export type Spins = Readonly<Record<string, number>>;

/** What each spin multiplicity is called, from a singlet, which has no unpaired electrons, up. */
const NAMES = ["singlet", "doublet", "triplet", "quartet", "quintet", "sextet", "septet"];

/** The most unpaired electrons the menu offers, which is a septet. */
export const MOST_MULTIPLICITY = NAMES.length;

/**
 * @param multiplicity a spin multiplicity
 * @returns what it is called, such as triplet
 */
export function spinName(multiplicity: number): string {
  return NAMES[multiplicity - 1] ?? `${multiplicity}-fold`;
}

/**
 * @param scope what the calculation is of
 * @returns the key its spin is kept under: an atom of the molecule if it is of one, and the empty string for the whole session, which is also what a
 *     calculation of each molecule uses for the whole
 */
export function spinKey(scope: OrbitalScope): string {
  return scope.kind === "one" ? scope.atom : "";
}

/**
 * @param scope what the calculation is of
 * @returns an atom of the molecule if it is of one, and null for the whole session
 */
export function fragmentOf(scope: OrbitalScope): string | null {
  return scope.kind === "one" ? scope.atom : null;
}

/**
 * @param spins the spins chosen
 * @param scope what the calculation is of
 * @returns the multiplicity chosen for it, or null for the lowest the electron count allows
 */
export function spinOf(spins: Spins, scope: OrbitalScope): number | null {
  return spins[spinKey(scope)] ?? null;
}

/** One spin that can be chosen, and whether it can be for what is being calculated. */
export interface SpinChoice {
  multiplicity: number;
  /** What it is called with how many electrons are unpaired, such as "Triplet (2 unpaired)". */
  label: string;
  disabled: boolean;
  /** Why it cannot be chosen, or null if it can. */
  reason: string | null;
}

/**
 * Lists the spins from a singlet to a septet.
 *
 * @param electrons how many electrons there are, or null if that is not known yet, when none is ruled out for it
 * @param kind the kind of calculation
 * @returns every spin, lowest first
 */
export function spinChoices(electrons: number | null, kind: OrbitalKind): SpinChoice[] {
  return NAMES.map((name, at) => {
    const multiplicity = at + 1;
    const unpaired = multiplicity - 1;
    const label = `${name[0]?.toUpperCase()}${name.slice(1)}${unpaired > 0 ? ` (${unpaired} unpaired)` : ""}`;
    let reason: string | null = null;
    if (kind === "rhf" && multiplicity !== 1) {
      reason = "Closed-shell Hartree-Fock can only be a singlet";
    } else if (electrons !== null && multiplicity > electrons + 1) {
      reason = `There are only ${electrons} electrons`;
    } else if (electrons !== null && unpaired % 2 !== electrons % 2) {
      reason = `${electrons} electrons cannot have ${unpaired} unpaired: it needs ${electrons % 2 === 0 ? "an even" : "an odd"} number`;
    }
    return { multiplicity, label, disabled: reason !== null, reason };
  });
}

/**
 * Says which kind of calculation it is, but only where it is not the automatic one that would be made anyway.
 *
 * @param kind the kind of calculation
 * @returns the label of the button that opens the menu: the plain name, or the name and the kind, such as "Calculation · ROHF"
 */
export function calculationLabel(kind: OrbitalKind): string {
  return kind === "mulliken" ? "Calculation" : `Calculation · ${kind.toUpperCase()}`;
}

/**
 * @param spins the spins chosen
 * @returns the spins as a key that is the same whenever they are, to tell when a calculation is another
 */
export function spinsKey(spins: Spins): string {
  return Object.keys(spins)
    .sort()
    .map((key) => `${key}=${spins[key]}`)
    .join(",");
}

/**
 * @param charges where the partial charges come from
 * @param kind the kind of calculation, which the calculated ones are made with
 * @returns what the server calls the way of assigning them
 */
export function chargeModelFor(charges: "calculated" | "formal", kind: OrbitalKind): string {
  return charges === "formal" ? "formal" : kind;
}

/** How many electrons the session has and each of its molecules, as the server says. */
export interface Electrons {
  total: number;
  molecules: readonly { atom: string; electrons: number }[];
}

/**
 * @param scope what the calculation is of
 * @param electrons what the server said, or null if it has not yet
 * @returns how many electrons what is calculated has, or null if that is not known, or there is no one number because each molecule is calculated
 */
export function electronsFor(scope: OrbitalScope, electrons: Electrons | null): number | null {
  if (electrons === null || scope.kind === "each") {
    return null;
  }
  return scope.kind === "all" ? electrons.total : (electrons.molecules.find((each) => each.atom === scope.atom)?.electrons ?? null);
}

/** What the server says a calculation was. */
export interface RunSummary {
  model: string;
  multiplicity: number;
  iterations: number;
  elapsed_ms: number;
}

/**
 * @param run the last calculation the server made
 * @returns it in a line, such as "UHF · triplet · 14 iterations · 0.4 s"
 */
export function describeRun(run: RunSummary): string {
  const seconds = run.elapsed_ms < 100 ? `${run.elapsed_ms} ms` : `${(run.elapsed_ms / 1000).toFixed(1)} s`;
  return [run.model.toUpperCase(), spinName(run.multiplicity), `${run.iterations} ${run.iterations === 1 ? "iteration" : "iterations"}`, seconds].join(" · ");
}
