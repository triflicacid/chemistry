import type {GridShape, Surface} from "../../render/isosurface";
import type {ElfDisplay} from "../models/PropertiesModel";
import type {BasinMessage, BasinsResult} from "../session/messages";
import {fieldSurface} from "./gridField";
import {type DecodedField, decodeFloats} from "./orbitals";

/** The electron localisation function split into its basins, which are its electron pairs, on one grid. */
export interface DecodedBasins {
  /** The function at each point, from zero to one. */
  values: Float32Array;
  /** Which basin each point is in, as a whole number from one, or zero for a point that is in none. */
  labels: Float32Array;
  shape: GridShape;
  basins: readonly BasinMessage[];
}

/** What each result decoded to. */
const decoded = new WeakMap<BasinsResult, DecodedBasins | null>();

/**
 * @param result what the server said of the basins
 * @returns the function, the basin of each point and where the grid is, or null if there is none or they are not the size the grid says
 */
export function decodeBasins(result: BasinsResult): DecodedBasins | null {
  if (!decoded.has(result)) {
    decoded.set(result, decode(result));
  }
  return decoded.get(result) ?? null;
}

function decode(result: BasinsResult): DecodedBasins | null {
  if (result.status !== "complete" || result.values === null || result.labels === null || result.grid === null) {
    return null;
  }

  const count = result.grid.nx * result.grid.ny * result.grid.nz;
  const values = decodeFloats(result.values, count);
  const labels = decodeFloats(result.labels, count);
  return values && labels ? { values, labels, shape: result.grid, basins: result.basins } : null;
}

/** The colours of the kinds of electron pair. */
export const BASIN_COLOURS: Readonly<Record<BasinMessage["kind"], string>> = {
  "lone pair": "#e0568a",
  bond: "#3ba55d",
  "multicentre bond": "#e0a030",
  unattached: "#8a8f98",
};

/**
 * @param kind what kind of pair a basin is
 * @returns the colour it is drawn in
 */
export function basinColour(kind: BasinMessage["kind"]): string {
  return BASIN_COLOURS[kind];
}

/** What the surface of each basin was made from: the function, with every point that is not in the basin set to nothing. */
const masked = new WeakMap<DecodedBasins, Map<number, DecodedField>>();

/**
 * @param field the basins
 * @param id which basin
 * @returns the function where the basin is and nothing elsewhere, so that a surface of it is bounded by the basin
 */
function within(field: DecodedBasins, id: number): DecodedField {
  let held = masked.get(field);
  if (!held) {
    held = new Map();
    masked.set(field, held);
  }
  let each = held.get(id);
  if (!each) {
    const values = new Float32Array(field.values.length);
    for (let at = 0; at < values.length; at++) {
      if (field.labels[at] === id) {
        values[at] = field.values[at] as number;
      }
    }
    each = { values, shape: field.shape };
    held.set(id, each);
  }
  return each;
}

/** Which electron pairs are shown: all of them, only the lone pairs, or only the bonds. */
export type PairsShown = Exclude<ElfDisplay, "level">;

/**
 * @param basins some electron pairs
 * @param which which of them are shown
 * @returns those of the kind, which for the bonds includes those that join more than two atoms
 */
export function pairsShown(basins: readonly BasinMessage[], which: PairsShown): BasinMessage[] {
  return basins.filter((basin) => which === "pairs" || (which === "lone" ? basin.kind === "lone pair" : basin.kind === "bond" || basin.kind === "multicentre bond"));
}

/** A basin drawn as a surface. */
export interface BasinSurface {
  basin: BasinMessage;
  /** Where the function is above the level inside the basin, which is empty if it never reaches it. */
  surface: Surface;
}

/**
 * Finds the surface of each basin: where the function is above a level, but only inside that basin, so that neighbouring pairs are drawn as lobes of
 * their own and not as one.
 *
 * @param field the basins
 * @param level the value of the function the surfaces are at, from zero to one
 * @param which which of the electron pairs to draw
 * @returns the surface of each, in the order of the basins
 */
export function basinSurfaces(field: DecodedBasins, level: number, which: PairsShown = "pairs"): BasinSurface[] {
  return pairsShown(field.basins, which).map((basin) => ({ basin, surface: fieldSurface(within(field, basin.id), level) }));
}

/**
 * @param basin an electron pair
 * @param elements the element of each atom, by the identifier of the atom
 * @returns what it is, such as "O lone pair" or "C–H bond", without how many electrons it holds
 */
export function basinName(basin: BasinMessage, elements: ReadonlyMap<string, string>): string {
  const symbols = basin.atoms.map((id) => elements.get(id) ?? "?");
  switch (basin.kind) {
    case "lone pair":
      return `${symbols[0] ?? "?"} lone pair`;
    case "bond":
      return `${symbols.join("–")} bond`;
    case "multicentre bond":
      return `${symbols.join("–")} bond`;
    default:
      return "electron pair";
  }
}

/**
 * @param basin an electron pair
 * @returns how many electrons it holds, to a tenth
 */
export function basinElectrons(basin: BasinMessage): string {
  return basin.population.toFixed(1);
}

/**
 * Finds which electron pair a point is in, from the points of the grid round it.
 *
 * @param field the basins
 * @param x where to look, in the frame of the structure, in angstroms
 * @param y where to look
 * @param z where to look
 * @returns the pair whose basin holds the nearest of the eight points round the place that is in one, or null if none of them is
 */
export function basinAt(field: DecodedBasins, x: number, y: number, z: number): BasinMessage | null {
  const { nx, ny, nz, origin, spacing } = field.shape;
  const [fx, fy, fz] = [(x - origin.x) / spacing, (y - origin.y) / spacing, (z - origin.z) / spacing];
  let nearest = Number.POSITIVE_INFINITY;
  let id = 0;
  for (const i of [Math.floor(fx), Math.floor(fx) + 1]) {
    for (const j of [Math.floor(fy), Math.floor(fy) + 1]) {
      for (const k of [Math.floor(fz), Math.floor(fz) + 1]) {
        if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) {
          continue;
        }
        const label = field.labels[(k * ny + j) * nx + i] as number;
        const distance = (fx - i) ** 2 + (fy - j) ** 2 + (fz - k) ** 2;
        if (label > 0 && distance < nearest) {
          nearest = distance;
          id = label;
        }
      }
    }
  }
  return field.basins.find((basin) => basin.id === id) ?? null;
}
