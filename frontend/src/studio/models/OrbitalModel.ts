import {Observable} from "./observable";

export const MIN_ISOVALUE = 0.005;
export const MAX_ISOVALUE = 0.2;
export const DEFAULT_ISOVALUE = 0.04;
export const DEFAULT_OPACITY = 0.65;

/** The least share of an orbital a surface can be chosen to hold, as a percentage, and the most. */
export const MIN_SHARE = 50;
export const MAX_SHARE = 99;
export const DEFAULT_SHARE = 90;

/** The most orbitals of one set that are drawn at once, which keeps the work and the picture in hand when all are asked for. */
export const MAX_SHOWN = 12;

/** How many orbitals can be drawn at once, with null meaning every one, up to {@link MAX_SHOWN}. */
export const COUNTS: readonly (number | null)[] = [1, 2, 3, 4, 6, 8, null];

/** What the server calls a kind of Hartree-Fock calculation, with `mulliken` meaning its own choice. */
export type OrbitalKind = "mulliken" | "rhf" | "uhf" | "rohf";

/** The kinds of calculation, in the order they are offered, with what each means. */
export const ORBITAL_KINDS: readonly { value: OrbitalKind; label: string; title: string }[] = [
  { value: "mulliken", label: "Hartree-Fock (automatic)", title: "Closed-shell where the electrons pair up, otherwise unrestricted as a doublet" },
  { value: "rhf", label: "Hartree-Fock (RHF)", title: "Closed-shell restricted Hartree-Fock: one set of orbitals, two electrons in each filled one. Needs an even number of electrons" },
  { value: "uhf", label: "Hartree-Fock (UHF)", title: "Unrestricted Hartree-Fock: a set of orbitals for each spin, as a singlet if the electrons pair up and otherwise a doublet" },
  { value: "rohf", label: "Hartree-Fock (ROHF)", title: "Restricted open-shell Hartree-Fock: one set of orbitals, with one electron in the singly occupied ones" },
];

/**
 * What the orbitals are of: everything in the session together as one molecule, each molecule on its own, or one molecule on its own.
 */
export type OrbitalScope = { kind: "all" } | { kind: "each" } | { kind: "one"; atom: string };

/** The scope of everything in the session together. */
export const ALL: OrbitalScope = { kind: "all" };

export interface OrbitalChoice {
  /** The set of orbitals: the one both spins share, or one spin's. */
  spin: "restricted" | "alpha" | "beta";
  /** How far above the highest orbital that holds electrons it is: zero for that orbital, one for the next, minus one for the one below. */
  offset: number;
  /**
   * Whether this is the frontier of every set instead: the HOMO and the orbitals below it together with the LUMO and those above it, as many
   * each as the number drawn says. The spin and offset then mean nothing.
   */
  frontier?: boolean;
}

/** What colours an orbital's surfaces: the sign of each lobe, or what the orbital is made of, sigma or pi, whatever the sign. */
export type OrbitalColouring = "phase" | "character" | "type" | "class" | "energy";

/** Which orbital is drawn in the scene, of what and from which calculation, and how. */
export interface OrbitalState {
  /** The orbital shown, or null if none is. */
  selected: OrbitalChoice | null;
  /** The kind of calculation the orbitals come from. */
  kind: OrbitalKind;
  /** What the orbitals are of. */
  scope: OrbitalScope;
  /** How many orbitals are drawn, counted outward from the one chosen, or null for every one on that side of the gap, up to {@link MAX_SHOWN}. */
  count: number | null;
  /** What the surfaces are coloured by. */
  colouring: OrbitalColouring;
  /** The value of the orbital at its surface, in bohr to the power minus three halves; the surface is drawn at this and minus this. */
  isovalue: number;
  /** How opaque the surface is, from nearly clear to solid. */
  opacity: number;
  /** What the surfaces show. */
  display: OrbitalDisplay;
  /** How the level of a surface of the amplitude is chosen; a surface of the probability is always chosen by share. */
  levelBy: LevelBy;
  /** How much of the orbital the surface holds, as a percentage, where it is chosen by share. */
  share: number;
  /** How several orbitals drawn at once are shown. */
  combine: OrbitalCombine;
  /** Whether the surfaces are drawn as solid skins or as outlines. */
  style: OrbitalStyle;
  /** Which of the orbitals the slice plane takes, where it is shown one at a time, by its place among them. */
  sliceOrbital: number;
}

/**
 * @param isovalue a value of an orbital
 * @returns the nearest value a surface can be drawn at, to the thousandth
 */
export function clampIsovalue(isovalue: number): number {
  return Number.isFinite(isovalue) ? Math.round(Math.min(MAX_ISOVALUE, Math.max(MIN_ISOVALUE, isovalue)) * 1000) / 1000 : DEFAULT_ISOVALUE;
}

/**
 * @param opacity how opaque
 * @returns the nearest opacity a surface can have, to the hundredth
 */
export function clampOpacity(opacity: number): number {
  return Number.isFinite(opacity) ? Math.round(Math.min(1, Math.max(0.1, opacity)) * 100) / 100 : DEFAULT_OPACITY;
}

/**
 * @param first an orbital, or none
 * @param second another
 * @returns whether they are the same place in the same set
 */
export function sameOrbital(first: OrbitalChoice | null, second: OrbitalChoice | null): boolean {
  return (
    first === second ||
    (first !== null && second !== null && Boolean(first.frontier) === Boolean(second.frontier) && (first.frontier === true || (first.spin === second.spin && first.offset === second.offset)))
  );
}

/**
 * @param first a scope
 * @param second another
 * @returns whether they are of the same thing
 */
export function sameScope(first: OrbitalScope, second: OrbitalScope): boolean {
  return first.kind === second.kind && (first.kind !== "one" || first.atom === (second as { atom: string }).atom);
}

/**
 * What a surface of an orbital shows: its amplitude, as lobes that are positive or negative; or its probability, the squared amplitude, as
 * nested shells that hold half of it and then the share asked for, which shows where the electron is most likely to be.
 */
export type OrbitalDisplay = "amplitude" | "probability";

/**
 * How several orbitals drawn at once are shown: each as its own surface; merged into one probability that is the sum of theirs, drawn as
 * shells of where the electrons are most likely to be; or merged into one surface that is wherever any reaches its own level, coloured by
 * the orbital that is largest there.
 */
export type OrbitalCombine = "separate" | "sum" | "union";

/** How the surfaces of orbitals are drawn: as solid skins, or as outlines, the lines where they cross evenly spaced planes, which show what is behind them. */
export type OrbitalStyle = "solid" | "outline";

/** How the level a surface of an orbital is drawn at is chosen: as a value of the orbital, or as the share of it the surface holds. */
export type LevelBy = "isovalue" | "share";

/** Which orbital is drawn in the scene, and how. It is not remembered between visits, since an orbital belongs to one structure. */
export class OrbitalModel extends Observable<OrbitalState> {
  constructor() {
    super({ selected: null, kind: "mulliken", scope: ALL, count: 1, colouring: "phase", isovalue: DEFAULT_ISOVALUE, opacity: DEFAULT_OPACITY, display: "amplitude", levelBy: "isovalue", share: DEFAULT_SHARE, combine: "separate", style: "solid", sliceOrbital: 0 });
  }

  /**
   * @param selected the orbital to show, or null to show none
   */
  select(selected: OrbitalChoice | null): void {
    if (!sameOrbital(this.get().selected, selected)) {
      this.set({ ...this.get(), selected });
    }
  }

  /**
   * Chooses one orbital and only that one, which is what clicking a level or a bar of a chart means. The number drawn is put back to
   * one, because counting outward from the orbital chosen would redraw mostly the orbitals that were there already and the click
   * would seem to do nothing.
   *
   * @param selected the orbital to show
   */
  pick(selected: OrbitalChoice): void {
    const state = this.get();
    if (!sameOrbital(state.selected, selected) || state.count !== 1) {
      this.set({ ...state, selected, count: 1 });
    }
  }

  /**
   * Changes the kind of calculation, which has other orbitals, so what was shown is put away.
   *
   * @param kind the kind of calculation
   */
  kindTo(kind: OrbitalKind): void {
    if (this.get().kind !== kind) {
      this.set({ ...this.get(), kind, selected: null });
    }
  }

  /**
   * Changes what the orbitals are of, which has other orbitals, so what was shown is put away.
   *
   * @param scope what the orbitals are of
   */
  scopeTo(scope: OrbitalScope): void {
    if (!sameScope(this.get().scope, scope)) {
      this.set({ ...this.get(), scope, selected: null });
    }
  }

  /**
   * @param count how many orbitals to draw, counted outward from the one chosen, or null for every one
   */
  countTo(count: number | null): void {
    if (this.get().count !== count) {
      this.set({ ...this.get(), count });
    }
  }

  /**
   * @param colouring what to colour the surfaces by
   */
  colouringTo(colouring: OrbitalColouring): void {
    if (this.get().colouring !== colouring) {
      this.set({ ...this.get(), colouring });
    }
  }

  /**
   * @param isovalue the value of the orbital to draw its surface at
   */
  isovalueTo(isovalue: number): void {
    const clamped = clampIsovalue(isovalue);
    if (this.get().isovalue !== clamped) {
      this.set({ ...this.get(), isovalue: clamped });
    }
  }

  /**
   * @param display what the surfaces show
   */
  displayTo(display: OrbitalDisplay): void {
    if (this.get().display !== display) {
      this.set({ ...this.get(), display });
    }
  }

  /**
   * @param sliceOrbital which of the orbitals drawn the slice plane cuts, by its place among them
   */
  sliceOrbitalTo(sliceOrbital: number): void {
    if (this.get().sliceOrbital !== sliceOrbital) {
      this.set({ ...this.get(), sliceOrbital });
    }
  }

  /**
   * @param style whether to draw the surfaces as solid skins or as outlines
   */
  styleTo(style: OrbitalStyle): void {
    if (this.get().style !== style) {
      this.set({ ...this.get(), style });
    }
  }

  /**
   * @param combine how several orbitals drawn at once are shown
   */
  combineTo(combine: OrbitalCombine): void {
    if (this.get().combine !== combine) {
      this.set({ ...this.get(), combine });
    }
  }

  /**
   * @param levelBy how the level of a surface of the amplitude is chosen
   */
  levelByTo(levelBy: LevelBy): void {
    if (this.get().levelBy !== levelBy) {
      this.set({ ...this.get(), levelBy });
    }
  }

  /**
   * @param share how much of the orbital the surface is to hold, as a percentage, which is held to what can be drawn
   */
  shareTo(share: number): void {
    const clamped = Number.isFinite(share) ? Math.round(Math.min(MAX_SHARE, Math.max(MIN_SHARE, share))) : DEFAULT_SHARE;
    if (this.get().share !== clamped) {
      this.set({ ...this.get(), share: clamped });
    }
  }

  /**
   * Chooses the isovalue and draws the surfaces by it, which is what dragging the marker on the chart of the share inside means.
   *
   * @param isovalue the value of the orbital to draw its surface at
   */
  pickIsovalue(isovalue: number): void {
    const clamped = clampIsovalue(isovalue);
    if (this.get().isovalue !== clamped || this.get().levelBy !== "isovalue") {
      this.set({ ...this.get(), isovalue: clamped, levelBy: "isovalue" });
    }
  }

  /**
   * @param opacity how opaque to draw the surface
   */
  opacityTo(opacity: number): void {
    const clamped = clampOpacity(opacity);
    if (this.get().opacity !== clamped) {
      this.set({ ...this.get(), opacity: clamped });
    }
  }
}
