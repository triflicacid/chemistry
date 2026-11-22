import type {GridShape} from "../../render/isosurface";
import type {
    FieldResult,
    OrbitalChannel,
    OrbitalLevel,
    OrbitalsResult,
    OrbitalTarget,
    SessionState
} from "../session/messages";
import {MAX_SHOWN, type OrbitalChoice, type OrbitalScope} from "../models/OrbitalModel";

/** How many electronvolts there are in a hartree. */
export const EV_PER_HARTREE = 27.211386;

/** Orbitals whose energies differ by less than this, in hartree, are drawn as one level shared between them. */
const DEGENERATE = 1e-4;

/**
 * @param hartree an energy in hartree
 * @returns it in electronvolts
 */
export function toEv(hartree: number): number {
  return hartree * EV_PER_HARTREE;
}

/** A field that has arrived, ready to make a surface of. */
export interface DecodedField {
  values: Float32Array;
  shape: GridShape;
}

/**
 * @param field what the server said of a field
 * @returns its values and where its grid is, or null if it has none or they are not the size the grid says
 */
export function decodeField(field: FieldResult): DecodedField | null {
  if (!decoded.has(field)) {
    decoded.set(field, decode(field));
  }
  return decoded.get(field) ?? null;
}

/** What each field decoded to, so that several parts of the page drawing one field decode it once. */
const decoded = new WeakMap<FieldResult, DecodedField | null>();

/**
 * @param field what the server said of a field
 * @returns its values and where its grid is, or null if it has none or they are not the size the grid says
 */
function decode(field: FieldResult): DecodedField | null {
  if (field.status !== "complete" || field.values === null || field.grid === null) {
    return null;
  }

  const values = decodeFloats(field.values, field.grid.nx * field.grid.ny * field.grid.nz);
  return values ? { values, shape: field.grid } : null;
}

/**
 * @param base64 numbers as little-endian 32-bit floats in base64, as the server sends a field
 * @param count how many numbers there should be
 * @returns the numbers, or null if there are not that many
 */
export function decodeFloats(base64: string, count: number): Float32Array | null {
  const text = atob(base64);
  if (Math.floor(text.length / 4) !== count) {
    return null;
  }

  const bytes = new DataView(new ArrayBuffer(text.length));
  for (let i = 0; i < text.length; i++) {
    bytes.setUint8(i, text.charCodeAt(i));
  }
  const values = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    values[i] = bytes.getFloat32(i * 4, true);
  }
  return values;
}

/**
 * @param levels every orbital
 * @param homo the index of the highest occupied orbital
 * @param around how many orbitals to keep each side of the gap between the highest occupied and the lowest empty
 * @returns the orbitals nearest the gap, which are the ones that matter for reactions, in energy order
 */
export function nearGap<T extends { index: number }>(levels: readonly T[], homo: number, around: number): T[] {
  return levels.filter((level) => level.index > homo - around && level.index <= homo + around);
}

/** An orbital placed on the diagram. */
export interface PlacedLevel extends OrbitalLevel {
  /** Which of the orbitals at this energy it is, counting from zero. */
  column: number;
  /** How many orbitals there are at this energy, which share the width between them. */
  columns: number;
}

/**
 * Gives orbitals of the same energy a column each, so that the lines for them sit side by side and none hides another.
 *
 * @param levels some orbitals, lowest in energy first
 * @returns each with where among the orbitals of its energy it is
 */
export function place(levels: readonly OrbitalLevel[]): PlacedLevel[] {
  const placed: PlacedLevel[] = [];
  let start = 0;
  while (start < levels.length) {
    let end = start + 1;
    while (end < levels.length && Math.abs((levels[end] as OrbitalLevel).energy - (levels[start] as OrbitalLevel).energy) < DEGENERATE) {
      end++;
    }
    for (let i = start; i < end; i++) {
      placed.push({ ...(levels[i] as OrbitalLevel), column: i - start, columns: end - start });
    }
    start = end;
  }
  return placed;
}

/**
 * @param offset how far above the highest orbital that holds electrons an orbital is, which is negative below it
 * @returns what chemists call it: HOMO, LUMO, HOMO-1 and so on
 */
export function nameAt(offset: number): string {
  if (offset <= 0) {
    return offset === 0 ? "HOMO" : `HOMO−${-offset}`;
  }
  return offset === 1 ? "LUMO" : `LUMO+${offset - 1}`;
}

/**
 * @param index which orbital it is
 * @param homo the index of the highest orbital that holds electrons, or -1 if none does
 * @returns what chemists call it: HOMO, LUMO, HOMO-1 and so on
 */
export function nameOf(index: number, homo: number): string {
  return nameAt(index - homo);
}

/**
 * @param scope what the orbitals are of
 * @param state the session, or null if there is none
 * @returns the molecules to calculate each on its own, as an atom of each: none for everything together, every molecule for
 *     each on its own, and the one for a chosen molecule, which becomes everything together if the session no longer has it
 */
export function fragmentsFor(scope: OrbitalScope, state: SessionState | null): string[] {
  if (state === null || scope.kind === "all") {
    return [];
  }
  if (scope.kind === "each") {
    return state.fragments.map((fragment) => fragment.atoms[0]).filter((atom): atom is string => atom !== undefined);
  }
  return state.atoms.some((atom) => atom.id === scope.atom) ? [scope.atom] : [];
}

/**
 * @param channel a set of orbitals
 * @param index one of its orbitals
 * @returns that orbital by its place relative to the gap, which is how a choice is made
 */
export function choiceOf(channel: Pick<OrbitalChannel, "spin" | "homo">, index: number): OrbitalChoice {
  return { spin: channel.spin, offset: index - (channel.homo ?? -1) };
}

/**
 * @param choice the orbital chosen, by its place relative to the gap
 * @returns which way more orbitals are counted from it: away from the gap, so down from the HOMO and up from the LUMO
 */
function outward(choice: OrbitalChoice): 1 | -1 {
  return choice.offset <= 0 ? -1 : 1;
}

/**
 * @param choice the orbital chosen
 * @param count how many orbitals are drawn, counted outward from it, or null for every one up to {@link MAX_SHOWN}
 * @param place the place of an orbital relative to the gap
 * @returns whether that orbital is one of those drawn
 */
export function covers(choice: OrbitalChoice | null, count: number | null, place: OrbitalChoice): boolean {
  if (choice === null || choice.spin !== place.spin) {
    return false;
  }
  const away = (place.offset - choice.offset) * outward(choice);
  return away >= 0 && away < Math.min(count ?? MAX_SHOWN, MAX_SHOWN);
}

/**
 * Finds the orbitals a choice names in each molecule: the HOMO of each, say, and the ones beyond it if more than one is drawn. A
 * molecule that has no such orbital, because it has no set of that spin or fewer orbitals, is left out of that one.
 *
 * @param result the levels the server last said
 * @param choice the orbital chosen
 * @param count how many orbitals are drawn, counted outward from the one chosen, or null for every one up to {@link MAX_SHOWN}
 * @returns the orbitals to evaluate, those of each molecule together
 */
export function resolveTargets(result: OrbitalsResult, choice: OrbitalChoice, count: number | null = 1): OrbitalTarget[] {
  const targets: OrbitalTarget[] = [];
  for (const channel of result.channels) {
    if (channel.spin !== choice.spin) {
      continue;
    }
    for (let step = 0; step < Math.min(count ?? MAX_SHOWN, MAX_SHOWN); step++) {
      const orbital = (channel.homo ?? -1) + choice.offset + step * outward(choice);
      if (orbital >= 0 && orbital < channel.levels.length) {
        targets.push({ fragment: channel.fragment, spin: channel.spin, orbital });
      }
    }
  }
  return targets;
}

/**
 * @param result the levels the server last said, or null if it has not
 * @param kind the kind of calculation that was asked for
 * @param fragments the molecules that were asked for
 * @returns whether the result is the answer to that, and not to something asked for before it
 */
export function answers(result: OrbitalsResult | null, kind: string, fragments: readonly string[]): result is OrbitalsResult {
  return result !== null && result.requested === kind && result.fragments.length === fragments.length && result.fragments.every((atom, i) => atom === fragments[i]);
}

/**
 * @param fields the fields the server last said
 * @param targets the orbitals wanted
 * @param state the session
 * @returns the fields of those orbitals that are of the session as it is now, which are the ones that can be drawn
 */
export function fieldsFor(fields: readonly FieldResult[], targets: readonly OrbitalTarget[], state: SessionState | null): FieldResult[] {
  if (state === null) {
    return [];
  }
  return targets.flatMap((target) => {
    const found = fields.find(
      (field) =>
        field.session_id === state.session_id && field.version === state.version && field.fragment === target.fragment && field.spin === target.spin && field.orbital === target.orbital,
    );
    return found ? [found] : [];
  });
}

/** The frontier orbitals of one molecule, in hartree. */
export interface Frontier {
  /** An atom of the molecule, or null if the orbitals are of the session calculated as one. */
  fragment: string | null;
  /** The energy of its highest orbital that holds electrons. */
  homo: number;
  /** The energy of its lowest orbital that holds none. */
  lumo: number;
}

/**
 * @param channels the sets of orbitals, which may be of several molecules and, for an unrestricted calculation, have a set for each spin
 * @returns the frontier orbitals of each molecule, in the order the molecules first appear: the highest orbital that holds
 *     electrons in either spin and the lowest that holds none in either; a molecule without both is left out
 */
export function frontiers(channels: readonly OrbitalChannel[]): Frontier[] {
  const order: (string | null)[] = [];
  for (const channel of channels) {
    if (!order.includes(channel.fragment)) {
      order.push(channel.fragment);
    }
  }

  return order.flatMap((fragment) => {
    const own = channels.filter((channel) => channel.fragment === fragment);
    const filled = own.flatMap((channel) => (channel.homo === null ? [] : [(channel.levels[channel.homo] as OrbitalLevel).energy]));
    const empty = own.flatMap((channel) => {
      const next = channel.levels[(channel.homo ?? -1) + 1];
      return next ? [next.energy] : [];
    });
    return filled.length > 0 && empty.length > 0 ? [{ fragment, homo: Math.max(...filled), lumo: Math.min(...empty) }] : [];
  });
}

/**
 * @param all the frontier orbitals of each molecule
 * @returns for each pair the energy it costs to move an electron from the first molecule's HOMO to the second's LUMO, in hartree,
 *     which is the gap that matters for the first giving electrons to the second; null for a molecule with itself
 */
export function frontierGaps(all: readonly Frontier[]): (number | null)[][] {
  return all.map((donor, i) => all.map((acceptor, j) => (i === j ? null : acceptor.lumo - donor.homo)));
}

/**
 * @param gaps the gaps of each pair, as {@link frontierGaps} gives them
 * @returns the donor and acceptor of the smallest gap, or null if there is no pair
 */
export function smallestGap(gaps: readonly (number | null)[][]): [number, number] | null {
  let best: [number, number] | null = null;
  gaps.forEach((row, i) =>
    row.forEach((gap, j) => {
      const found = best === null ? null : gaps[best[0]]?.[best[1]];
      if (gap !== null && (found === null || found === undefined || gap < found)) {
        best = [i, j];
      }
    }),
  );
  return best;
}

/**
 * Smooths orbital energies into a curve, each orbital a bell of the same width, so that clusters and gaps show.
 *
 * @param energies the orbital energies, in any one unit
 * @param width how wide each bell is, as its standard deviation, in the same unit
 * @param from the lowest energy to evaluate at
 * @param to the highest
 * @param points how many energies to evaluate at, evenly spaced from `from` to `to`
 * @returns the curve, whose area is the number of orbitals if the range covers them
 */
export function densityOfStates(energies: readonly number[], width: number, from: number, to: number, points: number): { x: number; y: number }[] {
  const scale = 1 / (width * Math.sqrt(2 * Math.PI));
  return Array.from({ length: points }, (_, i) => {
    const x = from + ((to - from) * i) / Math.max(1, points - 1);
    return { x, y: energies.reduce((sum, energy) => sum + scale * Math.exp(-0.5 * ((x - energy) / width) ** 2), 0) };
  });
}

/**
 * How much of an orbital lies inside the surface drawn at each value: the share of its squared amplitude, summed over the grid, at
 * the points where its amplitude is at least that value. A bigger value draws a surface that holds less of the orbital.
 *
 * @param values the orbital at each point of a grid
 * @param levels the values to find the share at, in increasing order
 * @returns the share, from zero to one, at each of those values
 */
export function enclosedShare(values: Float32Array, levels: readonly number[]): number[] {
  // each point counts towards every level up to its own amplitude, so it is added once at the highest such level and summed down
  const at = new Array<number>(levels.length).fill(0);
  let total = 0;
  for (const value of values) {
    const weight = value * value;
    total += weight;
    const amplitude = Math.abs(value);
    let low = 0;
    let high = levels.length;
    while (low < high) {
      const middle = (low + high) >> 1;
      if ((levels[middle] as number) <= amplitude) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    // low is how many levels the amplitude reaches, and the last of them is index low - 1
    if (low > 0) {
      at[low - 1] = (at[low - 1] as number) + weight;
    }
  }

  const share = new Array<number>(levels.length).fill(0);
  let running = 0;
  for (let i = levels.length - 1; i >= 0; i--) {
    running += at[i] as number;
    share[i] = total > 0 ? running / total : 0;
  }
  return share;
}

/** An orbital that is ready to be drawn. */
export interface DrawnOrbital {
  /** Its values on a grid. */
  field: DecodedField;
  /** How much of it is pi, or null if the atoms are not flat enough for it to be sigma or pi. */
  pi: number | null;
}

/**
 * @param result the levels the server last said
 * @param targets the orbitals wanted
 * @param fields the fields the server last said
 * @param state the session
 * @returns the orbitals among those wanted whose values have arrived for the session as it is now, each with how much of it is pi, which is
 *     what colours it when it is coloured by character
 */
export function drawnOrbitals(result: OrbitalsResult, targets: readonly OrbitalTarget[], fields: readonly FieldResult[], state: SessionState | null): DrawnOrbital[] {
  return targets.flatMap((target) => {
    const decoded = fieldsFor(fields, [target], state).flatMap((field) => decodeField(field) ?? []);
    const pi = result.channels.find((channel) => channel.fragment === target.fragment && channel.spin === target.spin)?.levels[target.orbital]?.pi ?? null;
    return decoded.map((field) => ({ field, pi }));
  });
}
