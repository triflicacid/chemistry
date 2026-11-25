import {Color} from "three";
import {type GridShape, isosurface, type Surface} from "../../render/isosurface";
import {diverging} from "../../render/scale";
import type {DensityResult, EspResult} from "../session/messages";
import {KCAL_PER_HARTREE} from "./forceArrows";
import {decodeFloats} from "./orbitals";

/** The electron density on a grid. */
export interface DecodedDensity {
  /** The density at each point, in electrons per cubic bohr. */
  density: Float32Array;
  shape: GridShape;
}

/** The electron density and the electrostatic potential on one grid. */
export interface DecodedEsp extends DecodedDensity {
  /** The potential at each point, in hartree per elementary charge, which is NaN where it was not found. */
  potential: Float32Array;
}

/** What each field decoded to. */
const decoded = new WeakMap<EspResult, DecodedEsp | null>();

/**
 * @param field what the server said of a field
 * @returns its density and potential and where their grid is, or null if it is not of the potential or they are not the size the grid says
 */
export function decodeEsp(field: EspResult): DecodedEsp | null {
  if (!decoded.has(field)) {
    decoded.set(field, decode(field));
  }
  return decoded.get(field) ?? null;
}

function decode(field: EspResult): DecodedEsp | null {
  if (field.status !== "complete" || field.density === null || field.potential === null || field.grid === null) {
    return null;
  }

  const count = field.grid.nx * field.grid.ny * field.grid.nz;
  const density = decodeFloats(field.density, count);
  const potential = decodeFloats(field.potential, count);
  return density && potential ? { density, potential, shape: field.grid } : null;
}

/** What each density decoded to. */
const decodedDensities = new WeakMap<DensityResult, DecodedDensity | null>();

/**
 * @param field what the server said of the density
 * @returns the density and where its grid is, or null if there is none or it is not the size the grid says
 */
export function decodeDensity(field: DensityResult): DecodedDensity | null {
  if (!decodedDensities.has(field)) {
    const count = field.grid ? field.grid.nx * field.grid.ny * field.grid.nz : 0;
    const density = field.status === "complete" && field.density !== null && field.grid !== null ? decodeFloats(field.density, count) : null;
    decodedDensities.set(field, density && field.grid ? { density, shape: field.grid } : null);
  }
  return decodedDensities.get(field) ?? null;
}

/** The last surface made of each density, which is kept for as long as the isovalue it was made at is asked for. */
const densitySurfaces = new WeakMap<DecodedDensity, { isovalue: number; made: Surface }>();

/**
 * @param field the density
 * @param isovalue the density the surface is at, in electrons per cubic bohr
 * @returns the surface of that density, which is empty if the density never reaches it
 */
export function densitySurface(field: DecodedDensity, isovalue: number): Surface {
  const held = densitySurfaces.get(field);
  if (held && held.isovalue === isovalue) {
    return held.made;
  }
  const made = isosurface(field.density, field.shape, isovalue);
  densitySurfaces.set(field, { isovalue, made });
  return made;
}

/** An atom as far as colouring a surface by it goes. */
export interface SurfaceAtom {
  /** Where it is, in the frame of the surface, in angstroms. */
  position: { x: number; y: number; z: number };
  /** Its colour, as hex. */
  colour: string;
  /** Its van der Waals radius, in angstroms. */
  radius: number;
}

/**
 * Colours each vertex of a surface as the atom it belongs to, which is the atom whose van der Waals sphere it is deepest in: the nearest by
 * distance squared less radius squared, so that a small hydrogen does not lose to a big atom beside it.
 *
 * @param surface the surface
 * @param atoms the atoms, in the same frame
 * @returns the red, green and blue of each vertex, from 0 to 1, as three numbers a vertex, or grey if there are no atoms
 */
export function nearestAtomColours(surface: Surface, atoms: readonly SurfaceAtom[]): Float32Array {
  const colours = new Float32Array(surface.positions.length);
  const tints = atoms.map((atom) => new Color(atom.colour));
  for (let v = 0; v < surface.positions.length; v += 3) {
    const [x, y, z] = [surface.positions[v] as number, surface.positions[v + 1] as number, surface.positions[v + 2] as number];
    let best = -1;
    let least = Number.POSITIVE_INFINITY;
    for (let a = 0; a < atoms.length; a++) {
      const { position, radius } = atoms[a] as SurfaceAtom;
      const score = (x - position.x) ** 2 + (y - position.y) ** 2 + (z - position.z) ** 2 - radius * radius;
      if (score < least) {
        least = score;
        best = a;
      }
    }
    const tint = tints[best] ?? MISSING;
    colours[v] = tint.r;
    colours[v + 1] = tint.g;
    colours[v + 2] = tint.b;
  }
  return colours;
}

/** The positive and the negative part of a signed field. */
export interface SignedSurfaces {
  /** Where the field is above the level. */
  positive: Surface;
  /** Where it is below minus the level. */
  negative: Surface;
}

/** The last surfaces made of each signed field, which are kept for as long as the isovalue they were made at is asked for. */
const signedSurfaces = new WeakMap<DecodedDensity, { isovalue: number; made: SignedSurfaces }>();

/**
 * @param field a density that is positive in some places and negative in others, such as the spin density
 * @param isovalue how far from zero the surfaces are, in electrons per cubic bohr
 * @returns a surface round where it is above the isovalue and another round where it is below minus the isovalue
 */
export function signedDensitySurfaces(field: DecodedDensity, isovalue: number): SignedSurfaces {
  const held = signedSurfaces.get(field);
  if (held && held.isovalue === isovalue) {
    return held.made;
  }
  // the negative part is the positive part of the field turned upside down
  const made = { positive: isosurface(field.density, field.shape, isovalue), negative: isosurface(field.density.map((value) => -value), field.shape, isovalue) };
  signedSurfaces.set(field, { isovalue, made });
  return made;
}

/**
 * Reads a field at a point between the points of its grid.
 *
 * @param values the field at each point of the grid, running along x fastest, then y, then z
 * @param shape where the grid is
 * @param x where to read it
 * @param y where to read it
 * @param z where to read it
 * @returns the value there, or NaN if none of the points round it has one
 */
export function sampleGrid(values: Float32Array, shape: GridShape, x: number, y: number, z: number): number {
  const at = [(x - shape.origin.x) / shape.spacing, (y - shape.origin.y) / shape.spacing, (z - shape.origin.z) / shape.spacing];
  const limits = [shape.nx - 1, shape.ny - 1, shape.nz - 1];
  const low = at.map((each, axis) => Math.min(Math.max(Math.floor(each), 0), (limits[axis] as number) - 1));
  const fraction = at.map((each, axis) => Math.min(Math.max(each - (low[axis] as number), 0), 1));

  let sum = 0;
  let weights = 0;
  for (let corner = 0; corner < 8; corner++) {
    const [di, dj, dk] = [corner & 1, (corner >> 1) & 1, (corner >> 2) & 1];
    const weight =
      (di ? (fraction[0] as number) : 1 - (fraction[0] as number)) * (dj ? (fraction[1] as number) : 1 - (fraction[1] as number)) * (dk ? (fraction[2] as number) : 1 - (fraction[2] as number));
    const value = values[(((low[2] as number) + dk) * shape.ny + (low[1] as number) + dj) * shape.nx + (low[0] as number) + di] ?? Number.NaN;
    if (weight > 0 && !Number.isNaN(value)) {
      sum += weight * value;
      weights += weight;
    }
  }
  return weights > 0 ? sum / weights : Number.NaN;
}

/** The least magnitude, in hartree per elementary charge, that the colours are spread over, so that a nearly uniform potential does not look strong. */
export const LEAST_LIMIT = 0.005;

/** A surface of the electron density with the potential at each of its vertices. */
export interface EspSurface {
  surface: Surface;
  /** The potential at each vertex, in hartree per elementary charge, NaN where there was none to read. */
  values: Float32Array;
  /** The magnitude at which the colours are as strong as they get when the range is left to the data, in hartree per elementary charge. */
  limit: number;
}

/** The last surface made of each field, which is kept for as long as the isovalue it was made at is asked for. */
const surfaces = new WeakMap<DecodedEsp, { isovalue: number; made: EspSurface }>();

/**
 * Finds the surface of the electron density at a value and the potential on it.
 *
 * @param esp the density and potential
 * @param isovalue the density the surface is at, in electrons per cubic bohr
 * @returns the surface, the potential at each of its vertices, and how far the colours should reach to show them
 */
export function espSurface(esp: DecodedEsp, isovalue: number): EspSurface {
  const held = surfaces.get(esp);
  if (held && held.isovalue === isovalue) {
    return held.made;
  }

  const surface = densitySurface(esp, isovalue);
  const values = new Float32Array(surface.positions.length / 3);
  for (let v = 0; v < values.length; v++) {
    values[v] = sampleGrid(esp.potential, esp.shape, surface.positions[v * 3] as number, surface.positions[v * 3 + 1] as number, surface.positions[v * 3 + 2] as number);
  }

  const made = { surface, values, limit: autoLimit(values) };
  surfaces.set(esp, { isovalue, made });
  return made;
}

/**
 * @param values the potential at the vertices of a surface
 * @returns how far from zero the colours should reach: the size that nine tenths of the vertices are within, so that a few spikes near the atoms do
 *     not wash the rest out, but never less than {@link LEAST_LIMIT}
 */
export function autoLimit(values: ArrayLike<number>): number {
  const sizes = Array.from(values, Math.abs).filter((size) => Number.isFinite(size)).sort((a, b) => a - b);
  // the smallest size that at least nine tenths of the values are within
  return Math.max(LEAST_LIMIT, sizes[Math.ceil(sizes.length * 0.9) - 1] ?? 0);
}

/** How many steps the colour scale is cut into, which is more than the eye can tell apart. */
const STEPS = 256;

/** The colour of a point with no potential, which is no more than a point between two that have, and so never seen except at the rim. */
const MISSING = new Color("#b8bcc4");

/** The scale as a table. */
const TABLE: Color[] = Array.from({ length: 2 * STEPS + 1 }, (_, i) => new Color(diverging((i - STEPS) / STEPS, 1)));

/**
 * @param values the potential at each vertex
 * @param limit the magnitude at which the colour is as strong as it gets
 * @returns the red, green and blue of each vertex, from 0 to 1, as three numbers a vertex: red where the potential is negative, blue where it is positive
 */
export function vertexColours(values: ArrayLike<number>, limit: number): Float32Array {
  const colours = new Float32Array(values.length * 3);
  for (let v = 0; v < values.length; v++) {
    const value = values[v] as number;
    const colour = Number.isNaN(value) || !(limit > 0) ? MISSING : (TABLE[Math.round((Math.max(-1, Math.min(1, value / limit)) + 1) * STEPS)] as Color);
    colours[v * 3] = colour.r;
    colours[v * 3 + 1] = colour.g;
    colours[v * 3 + 2] = colour.b;
  }
  return colours;
}

/**
 * @param hartree a potential in hartree per elementary charge
 * @returns it in kilocalories per mole per elementary charge, which is what a chemist has a feel for
 */
export function toKcalPerCharge(hartree: number): number {
  return hartree * KCAL_PER_HARTREE;
}
