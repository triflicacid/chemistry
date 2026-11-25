import type {Bar} from "../controls/BarChart";
import type {OrbitalChoice} from "../models/OrbitalModel";
import type {OrbitalChannel, OrbitalLevel} from "../session/messages";
import {choiceOf, covers, nameOf} from "./orbitals";

/** What kinds of function an orbital's share is split into, in the order the server sends them. */
export const KINDS = ["s", "p", "d and higher"] as const;

/**
 * @param level an orbital
 * @returns how much of it is on each atom, over all kinds of function
 */
export function atomShares(level: Pick<OrbitalLevel, "weights">): number[] {
  const shares: number[] = [];
  for (let at = 0; at + KINDS.length <= level.weights.length; at += KINDS.length) {
    shares.push(level.weights.slice(at, at + KINDS.length).reduce((sum, weight) => sum + weight, 0));
  }
  return shares;
}

/**
 * @param level an orbital
 * @returns how much of it is in s functions, in p functions and in d and higher, over all atoms, which is its character
 */
export function characterOf(level: Pick<OrbitalLevel, "weights">): number[] {
  const totals = KINDS.map(() => 0);
  level.weights.forEach((weight, i) => {
    totals[i % KINDS.length] = (totals[i % KINDS.length] as number) + weight;
  });
  return totals;
}

/**
 * How many atoms an orbital is spread over.
 *
 * @param level an orbital
 * @returns the effective number of atoms it is on, or zero if it is on none
 */
export function participation(level: Pick<OrbitalLevel, "weights">): number {
  const shares = atomShares(level);
  const total = shares.reduce((sum, share) => sum + share, 0);
  const squares = shares.reduce((sum, share) => sum + (share / total) ** 2, 0);
  return total > 0 && squares > 0 ? 1 / squares : 0;
}

/** The molecule an orbital is mostly on. */
export interface Dominant {
  /** Which molecule, by its place in the list given. */
  molecule: number;
  /** How much of the orbital is on it, from zero to one. */
  share: number;
}

/**
 * @param level an orbital
 * @param atoms the atoms the orbital's weights are of, in order
 * @param molecules the atoms of each molecule
 * @returns the molecule most of the orbital is on, or null if there are no molecules or the orbital is on none of them
 */
export function dominantMolecule(level: Pick<OrbitalLevel, "weights">, atoms: readonly string[], molecules: readonly (readonly string[])[]): Dominant | null {
  const shares = atomShares(level);
  const total = shares.reduce((sum, share) => sum + share, 0);
  let best: Dominant | null = null;
  molecules.forEach((own, molecule) => {
    const held = atoms.reduce((sum, atom, i) => sum + (own.includes(atom) ? (shares[i] ?? 0) : 0), 0);
    if (total > 0 && (best === null || held / total > best.share)) {
      best = { molecule, share: held / total };
    }
  });
  return best;
}

/** How far apart an orbital is for the two spins. */
export interface Splitting {
  /** Which orbital of each set, counting from the lowest in energy at zero. */
  index: number;
  /** The spin-up energy less the spin-down energy, in hartree. */
  split: number;
}

/**
 * @param alpha the spin-up set of orbitals
 * @param beta the spin-down set, of the same molecule
 * @returns for each orbital that both sets have, how much higher the spin-up one is than the spin-down one, which says how strongly the
 *     electrons of an open-shell molecule polarise: zero for a closed-shell one
 */
export function spinSplittings(alpha: { levels: readonly OrbitalLevel[] }, beta: { levels: readonly OrbitalLevel[] }): Splitting[] {
  const count = Math.min(alpha.levels.length, beta.levels.length);
  return Array.from({ length: count }, (_, index) => ({ index, split: (alpha.levels[index] as OrbitalLevel).energy - (beta.levels[index] as OrbitalLevel).energy }));
}

/** The bars of the localisation chart, and what a click on each of them means. */
export interface LocalisationBars {
  bars: Bar[];
  /** The orbital each bar is of, by its place relative to the gap, which is what is chosen when the bar is clicked. */
  choices: Map<string, OrbitalChoice>;
  /** The bars of the orbitals that are drawn now. */
  picked: Set<string>;
}

/**
 * Makes the bars of how many atoms each orbital is spread over, one for each orbital of each set, and says which orbital a click on each
 * chooses and which bars are of the orbitals that are drawn now.
 *
 * @param channels the sets of orbitals to make bars of, which may be only those near the gap
 * @param selected the orbital drawn, or null if none is
 * @param count how many orbitals are drawn, counted outward from the one chosen, or null for every one
 * @param molecules the atoms of each molecule, to say which one holds most of an orbital of everything together
 * @param nameOfMolecule names a molecule from an atom of it
 * @returns the bars, with what choosing each means
 */
export function localisationBars(
  channels: readonly (Omit<OrbitalChannel, "levels"> & { levels: readonly OrbitalLevel[] })[],
  selected: OrbitalChoice | null,
  count: number | null,
  molecules: readonly (readonly string[])[],
  nameOfMolecule: (atom: string | null) => string | null,
): LocalisationBars {
  const choices = new Map<string, OrbitalChoice>();
  const picked = new Set<string>();
  const bars = channels.flatMap((channel) =>
    channel.levels
      .filter((level) => level.weights.length > 0)
      .map((level): Bar => {
        const key = `${channel.fragment ?? ""}|${channel.spin}|${level.index}`;
        const choice = choiceOf(channel, level.index);
        choices.set(key, choice);
        if (covers(selected, count, choice)) {
          picked.add(key);
        }

        const dominant = channel.fragment === null && molecules.length > 1 ? dominantMolecule(level, channel.atoms, molecules) : null;
        const spin = channel.spin === "alpha" ? "↑ " : channel.spin === "beta" ? "↓ " : "";
        const owner = channel.fragment !== null && channels.length > 1 ? `${nameOfMolecule(channel.fragment) ?? ""} ` : "";
        const where = dominant ? (nameOfMolecule(molecules[dominant.molecule]?.[0] ?? null) ?? "one molecule") : null;
        return {
          key,
          label: `${owner}${spin}${nameOf(level.index, channel.homo ?? -1)}`.trim(),
          parts: [{ name: level.occupancy > 0 ? "filled" : "empty", value: participation(level), slot: level.occupancy > 0 ? 1 : 2 }],
          title: dominant && where ? `${Math.round(dominant.share * 100)}% of it is on ${where}` : undefined,
        };
      }),
  );
  return { bars, choices, picked };
}

/** The sigma and pi share of each orbital, as bars, and what a click on each means. */
export interface PiBars extends LocalisationBars {
  /** How far the atoms lie off their plane, for each set that has a plane, in angstroms; used to say why there are no bars. */
  flatness: number[];
}

/**
 * Makes the bars of how much of each orbital is sigma and how much is pi, one for each orbital of each flat set, and says which orbital a click
 * on each chooses and which bars are of the orbitals that are drawn now. A set whose atoms do not lie flat has no bars, since sigma and pi mean
 * nothing for it.
 *
 * @param channels the sets of orbitals to make bars of, which may be only those near the gap
 * @param selected the orbital drawn, or null if none is
 * @param count how many orbitals are drawn, counted outward from the one chosen, or null for every one
 * @param nameOfMolecule names a molecule from an atom of it
 * @returns the bars, with what choosing each means
 */
export function piBars(
  channels: readonly (Omit<OrbitalChannel, "levels"> & { levels: readonly OrbitalLevel[] })[],
  selected: OrbitalChoice | null,
  count: number | null,
  nameOfMolecule: (atom: string | null) => string | null,
): PiBars {
  const choices = new Map<string, OrbitalChoice>();
  const picked = new Set<string>();
  const bars = channels.flatMap((channel) =>
    channel.levels.flatMap((level): Bar[] => {
      if (level.pi === null) {
        return [];
      }

      const key = `${channel.fragment ?? ""}|${channel.spin}|${level.index}`;
      const choice = choiceOf(channel, level.index);
      choices.set(key, choice);
      if (covers(selected, count, choice)) {
        picked.add(key);
      }

      const spin = channel.spin === "alpha" ? "↑ " : channel.spin === "beta" ? "↓ " : "";
      const owner = channel.fragment !== null && channels.length > 1 ? `${nameOfMolecule(channel.fragment) ?? ""} ` : "";
      return [
        {
          key,
          label: `${owner}${spin}${nameOf(level.index, channel.homo ?? -1)}`.trim(),
          parts: [
            { name: "σ", value: 1 - level.pi, slot: 1 },
            { name: "π", value: level.pi, slot: 2 },
          ],
        },
      ];
    }),
  );
  return { bars, choices, picked, flatness: channels.flatMap((channel) => (channel.flatness === null ? [] : [channel.flatness])) };
}
