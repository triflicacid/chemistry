import {Color} from "three";
import type {Surface} from "../../render/isosurface";
import {NEUTRAL_COLOUR} from "../../render/scale";
import {type DecodedDensity, densitySurface, sampleGrid} from "./esp";
import type {OrbitalsResult, OrbitalTarget} from "../session/messages";
import type {DrawnOrbital} from "./orbitals";

/** Which frontier orbital a reactivity map shows: the HOMO, for where an electrophile attacks, or the LUMO, for where a nucleophile does. */
export type FrontierSide = "homo" | "lumo";

/** The colour of the strongest part of a map of the HOMO, which is the colour the Orbitals tab gives the HOMO. */
export const HOMO_REACTIVITY = "#e8590c";

/** The colour of the strongest part of a map of the LUMO, which is the colour the Orbitals tab gives the LUMO. */
export const LUMO_REACTIVITY = "#1971c2";

/** How close in energy, in hartree, a level must be to the HOMO or the LUMO to count as the same frontier level. */
export const NEAR_DEGENERATE = 0.005;

/**
 * Finds the orbitals a map of a frontier level is made of: the HOMO or the LUMO of each set, and the orbitals that share its energy.
 *
 * @param result the levels the server last said
 * @param side the HOMO or the LUMO
 * @returns the orbitals to evaluate, in every set and molecule
 */
export function frontierTargets(result: OrbitalsResult, side: FrontierSide): OrbitalTarget[] {
  return result.channels.flatMap((channel) => {
    const at = (channel.homo ?? -1) + (side === "homo" ? 0 : 1);
    const frontier = channel.levels[at];
    if (!frontier) {
      return [];
    }
    return channel.levels
      .filter((level) => Math.abs(level.energy - frontier.energy) < NEAR_DEGENERATE)
      .map((level) => ({ fragment: channel.fragment, spin: channel.spin, orbital: level.index }));
  });
}

/** A surface of the electron density, with how much of a frontier orbital is at each of its vertices. */
export interface ReactivitySurface {
  surface: Surface;
  /** The probability density of the orbitals at each vertex, in electrons per cubic bohr, counting each orbital as one electron. */
  values: Float32Array;
  /** The value at which the colours are as strong as they get, which a few spikes beyond do not move. */
  limit: number;
}

/**
 * Finds the surface of the electron density and, at each of its vertices, how much the frontier orbitals are there.
 *
 * @param density the electron density
 * @param isovalue the density the surface is at, in electrons per cubic bohr
 * @param orbitals the orbitals to read, on whatever grids they were worked out on
 * @returns the surface, the orbitals' probability density at each vertex, and the value the colours reach
 */
export function reactivitySurface(density: DecodedDensity, isovalue: number, orbitals: readonly DrawnOrbital[]): ReactivitySurface {
  const surface = densitySurface(density, isovalue);
  const values = probabilityAt(surface, orbitals);
  return { surface, values, limit: strongest(values) };
}

/**
 * @param surface a surface
 * @param orbitals the orbitals to read, on whatever grids they were worked out on
 * @returns the sum of their probability densities at each vertex, in electrons per cubic bohr, counting each orbital as one electron
 */
function probabilityAt(surface: Surface, orbitals: readonly DrawnOrbital[]): Float32Array {
  const values = new Float32Array(surface.positions.length / 3);
  for (const { field } of orbitals) {
    for (let v = 0; v < values.length; v++) {
      const amplitude = sampleGrid(field.values, field.shape, surface.positions[v * 3] as number, surface.positions[v * 3 + 1] as number, surface.positions[v * 3 + 2] as number);
      values[v] = (values[v] as number) + (Number.isNaN(amplitude) ? 0 : amplitude * amplitude);
    }
  }
  return values;
}

/** A surface of the electron density, with how much of the HOMO and how much of the LUMO is at each of its vertices. */
export interface DualSurface {
  surface: Surface;
  /** The probability density of the HOMO's orbitals at each vertex, in electrons per cubic bohr. */
  homo: Float32Array;
  /** The same for the LUMO's. */
  lumo: Float32Array;
  /** The value of the HOMO at which its share is as strong as it gets. */
  homoLimit: number;
  /** The value of the LUMO at which its share is as strong as it gets. */
  lumoLimit: number;
}

/**
 * Finds the surface of the electron density and, at each of its vertices, how much both frontier orbitals are there.
 *
 * @param density the electron density
 * @param isovalue the density the surface is at, in electrons per cubic bohr
 * @param homo the orbitals of the HOMO level, on whatever grids they were worked out on
 * @param lumo the orbitals of the LUMO level
 * @returns the surface and the probability density of each at every vertex
 */
export function dualSurface(density: DecodedDensity, isovalue: number, homo: readonly DrawnOrbital[], lumo: readonly DrawnOrbital[]): DualSurface {
  const surface = densitySurface(density, isovalue);
  const homoValues = probabilityAt(surface, homo);
  const lumoValues = probabilityAt(surface, lumo);
  return { surface, homo: homoValues, lumo: lumoValues, homoLimit: strongest(homoValues), lumoLimit: strongest(lumoValues) };
}

/**
 * Colours a surface by which frontier orbital is more there. Each is first taken as a share of its own strongest value, since the two differ
 * in size and the more compact would win everywhere otherwise, and the colour follows the LUMO's share less the HOMO's: the colour of the LUMO
 * where it is the greater, the colour of the HOMO where that is, and grey where neither is there or both are as much as each other.
 *
 * @param made the surface and both orbitals' values on it
 * @returns the red, green and blue of each vertex, from 0 to 1, as three numbers a vertex
 */
export function dualColours(made: DualSurface): Float32Array {
  const none = new Color(NEUTRAL_COLOUR);
  const homo = new Color(HOMO_REACTIVITY);
  const lumo = new Color(LUMO_REACTIVITY);
  const colours = new Float32Array(made.homo.length * 3);
  const mixed = new Color();
  for (let v = 0; v < made.homo.length; v++) {
    const h = made.homoLimit > 0 ? Math.min(1, (made.homo[v] as number) / made.homoLimit) : 0;
    const l = made.lumoLimit > 0 ? Math.min(1, (made.lumo[v] as number) / made.lumoLimit) : 0;
    const difference = l - h;
    mixed.copy(none).lerp(difference > 0 ? lumo : homo, Math.abs(difference));
    colours.set([mixed.r, mixed.g, mixed.b], v * 3);
  }
  return colours;
}

/**
 * @returns a CSS gradient from the colour of the HOMO through grey to the colour of the LUMO, to draw a legend with
 */
export function dualGradient(): string {
  return `linear-gradient(to right, ${HOMO_REACTIVITY}, ${NEUTRAL_COLOUR}, ${LUMO_REACTIVITY})`;
}

/**
 * @param field a field a slice can show
 * @returns whether it is a plane of the frontier orbital
 */
export function isFrontierMap(field: string): field is "homoMap" | "lumoMap" {
  return field === "homoMap" || field === "lumoMap";
}

/**
 * Adds the probabilities of some orbitals into one field, so that a plane can be cut through it.
 *
 * @param orbitals the orbitals to add, each counted as one electron
 * @returns the probability density at each point of the grid, in electrons per cubic bohr, or null if there are no orbitals
 */
export function reactivityField(orbitals: readonly DrawnOrbital[]): DecodedDensity | null {
  const first = orbitals[0];
  if (!first) {
    return null;
  }

  const { shape } = first.field;
  const density = new Float32Array(shape.nx * shape.ny * shape.nz);
  for (const { field } of orbitals) {
    const same = field.shape.nx === shape.nx && field.shape.ny === shape.ny && field.shape.nz === shape.nz && field.shape.spacing === shape.spacing;
    for (let k = 0; k < shape.nz; k++) {
      for (let j = 0; j < shape.ny; j++) {
        for (let i = 0; i < shape.nx; i++) {
          const at = (k * shape.ny + j) * shape.nx + i;
          const amplitude = same
            ? (field.values[at] as number)
            : sampleGrid(field.values, field.shape, shape.origin.x + i * shape.spacing, shape.origin.y + j * shape.spacing, shape.origin.z + k * shape.spacing);
          density[at] = (density[at] as number) + (Number.isNaN(amplitude) ? 0 : amplitude * amplitude);
        }
      }
    }
  }
  return { density, shape };
}

/**
 * @param values what is at each vertex, none of which is negative
 * @returns the value that 95 in 100 of the vertices are within, so that a few spikes do not wash the rest out, or zero if there are none
 */
export function strongest(values: ArrayLike<number>): number {
  const sorted = Array.from(values).sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.95) - 1)] ?? 0;
}

/**
 * @param side the orbital the map is of
 * @returns the colour of the strongest part of the map
 */
export function reactivityColour(side: FrontierSide): string {
  return side === "homo" ? HOMO_REACTIVITY : LUMO_REACTIVITY;
}

/**
 * @param values the orbitals' probability density at each vertex
 * @param limit the value at which the colour is as strong as it gets
 * @param side the orbital the map is of
 * @returns the red, green and blue of each vertex, from 0 to 1, as three numbers a vertex: grey where the orbital is not, and the colour of the
 *     orbital where it is most
 */
export function reactivityColours(values: ArrayLike<number>, limit: number, side: FrontierSide): Float32Array {
  const none = new Color(NEUTRAL_COLOUR);
  const most = new Color(reactivityColour(side));
  const colours = new Float32Array(values.length * 3);
  const mixed = new Color();
  for (let v = 0; v < values.length; v++) {
    mixed.copy(none).lerp(most, limit > 0 ? Math.max(0, Math.min(1, (values[v] as number) / limit)) : 0);
    colours.set([mixed.r, mixed.g, mixed.b], v * 3);
  }
  return colours;
}

/**
 * @param side the orbital the map is of
 * @returns a CSS gradient from where the orbital is not to where it is most, to draw a legend with
 */
export function reactivityGradient(side: FrontierSide): string {
  return `linear-gradient(to right, ${NEUTRAL_COLOUR}, ${reactivityColour(side)})`;
}
