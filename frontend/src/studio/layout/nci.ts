import {Color} from "three";
import {type GridShape, isosurface, type Surface} from "../../render/isosurface";
import type {NciResult} from "../session/messages";
import {sampleGrid} from "./gridField";
import {decodeFloats} from "./orbitals";

/** The density, in electrons per cubic bohr, above which a point is left out of a plot of non-covalent interactions. */
export const NCI_CUTOFF = 0.05;

/** The density, in electrons per cubic bohr, at which the colours are as strong as they get. */
export const NCI_LIMIT = 0.04;

/** The reduced density gradient given to a point that is left out, which is above any level a surface is drawn at. */
const LEFT_OUT = 100;

/** The two fields of a plot of non-covalent interactions on one grid. */
export interface DecodedNci {
  /** The reduced density gradient at each point, which has no unit. */
  gradient: Float32Array;
  /** The electron density at each point in electrons per cubic bohr, negative where the density is drawn together across the contact. */
  signed: Float32Array;
  shape: GridShape;
}

/** What each field decoded to. */
const decoded = new WeakMap<NciResult, DecodedNci | null>();

/**
 * @param field what the server said of the fields
 * @returns the fields and where their grid is, or null if it is not of the fields or they are not the size the grid says
 */
export function decodeNci(field: NciResult): DecodedNci | null {
  if (!decoded.has(field)) {
    decoded.set(field, decode(field));
  }
  return decoded.get(field) ?? null;
}

function decode(field: NciResult): DecodedNci | null {
  if (field.status !== "complete" || field.gradient === null || field.signed === null || field.grid === null) {
    return null;
  }

  const count = field.grid.nx * field.grid.ny * field.grid.nz;
  const gradient = decodeFloats(field.gradient, count);
  const signed = decodeFloats(field.signed, count);
  return gradient && signed ? { gradient, signed, shape: field.grid } : null;
}

/** The contacts at some level: where they are, and the signed density at each vertex of them. */
export interface NciSurface {
  surface: Surface;
  /** The signed density at each vertex, in electrons per cubic bohr. */
  values: Float32Array;
}

/** The last surface made of each field, which is kept for as long as the level it was made at is asked for. */
const surfaces = new WeakMap<DecodedNci, { level: number; made: NciSurface }>();

/**
 * Finds the surfaces that enclose where the reduced density gradient is below a level, outside the cores and bonds, and the signed density on them.
 *
 * @param field the two fields
 * @param level the reduced density gradient the surfaces are at, which has no unit
 * @returns the surfaces, which are empty if nothing is below the level
 */
export function nciSurface(field: DecodedNci, level: number): NciSurface {
  const held = surfaces.get(field);
  if (held && held.level === level) {
    return held.made;
  }

  // the surface goes round the part that is above its level, so the gradient is turned upside down to put the small values inside
  const inside = new Float32Array(field.gradient.length);
  for (let at = 0; at < inside.length; at++) {
    inside[at] = Math.abs(field.signed[at] as number) > NCI_CUTOFF ? -LEFT_OUT : -(field.gradient[at] as number);
  }
  const surface = isosurface(inside, field.shape, -level);

  const values = new Float32Array(surface.positions.length / 3);
  for (let v = 0; v < values.length; v++) {
    values[v] = sampleGrid(field.signed, field.shape, surface.positions[v * 3] as number, surface.positions[v * 3 + 1] as number, surface.positions[v * 3 + 2] as number);
  }

  const made = { surface, values };
  surfaces.set(field, { level, made });
  return made;
}

/** The colour where the density is drawn together across a contact, which is where atoms attract, as in a hydrogen bond. */
export const ATTRACTIVE = "#2f6fdb";

/** The colour where the density is neither, which is where atoms only touch, as in van der Waals contact. */
export const WEAK = "#3fbf6a";

/** The colour where the density is pushed apart across a contact, which is where atoms clash or are shut in a ring. */
export const REPULSIVE = "#e04b3f";

const STOPS = [new Color(ATTRACTIVE), new Color(WEAK), new Color(REPULSIVE)];

/**
 * @param value a signed density, in electrons per cubic bohr
 * @returns the red, green and blue of its colour, from 0 to 1: blue where it is negative and at least {@link NCI_LIMIT} in size, red where it is
 *     as positive, and green between
 */
export function nciColour(value: number): [number, number, number] {
  const place = (Math.max(-1, Math.min(1, Number.isNaN(value) ? 0 : value / NCI_LIMIT)) + 1) / 2;
  const from = Math.min(Math.floor(place * 2), 1);
  const mixed = (STOPS[from] as Color).clone().lerp(STOPS[from + 1] as Color, place * 2 - from);
  return [mixed.r, mixed.g, mixed.b];
}

/**
 * @param values the signed density at each vertex
 * @returns the red, green and blue of each vertex, from 0 to 1, as three numbers a vertex
 */
export function nciColours(values: ArrayLike<number>): Float32Array {
  const colours = new Float32Array(values.length * 3);
  for (let v = 0; v < values.length; v++) {
    colours.set(nciColour(values[v] as number), v * 3);
  }
  return colours;
}
