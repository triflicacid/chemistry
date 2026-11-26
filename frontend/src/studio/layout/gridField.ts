import {Color} from "three";
import {type GridShape, isosurface, type Surface} from "../../render/isosurface";
import type {ScalarFieldResult} from "../session/messages";
import {type DecodedField, decodeFloats} from "./orbitals";

/** The colour of a part of a surface that no atom is near enough to colour, which is no more than a point between two that have. */
const MISSING = new Color("#b8bcc4");

/** What each field decoded to. */
const decodedFields = new WeakMap<ScalarFieldResult, DecodedField | null>();

/**
 * @param field what the server said of a scalar field
 * @returns its values and where its grid is, or null if there is none or they are not the size the grid says
 */
export function decodeScalarField(field: ScalarFieldResult): DecodedField | null {
  if (!decodedFields.has(field)) {
    const count = field.grid ? field.grid.nx * field.grid.ny * field.grid.nz : 0;
    const values = field.status === "complete" && field.values !== null && field.grid !== null ? decodeFloats(field.values, count) : null;
    decodedFields.set(field, values && field.grid ? { values, shape: field.grid } : null);
  }
  return decodedFields.get(field) ?? null;
}

/** The last surface made of each field, which is kept for as long as the isovalue it was made at is asked for. */
const fieldSurfaces = new WeakMap<DecodedField, { isovalue: number; made: Surface }>();

/**
 * @param field a field that is highest inside what is to be enclosed, such as the electron density
 * @param isovalue the value the surface is at
 * @returns the surface of that value, which is empty if the field never reaches it
 */
export function fieldSurface(field: DecodedField, isovalue: number): Surface {
  const held = fieldSurfaces.get(field);
  if (held && held.isovalue === isovalue) {
    return held.made;
  }
  const made = isosurface(field.values, field.shape, isovalue);
  fieldSurfaces.set(field, { isovalue, made });
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
 * Colours each vertex of a surface as the atom it belongs to, which is the atom whose van der Waals sphere it is deepest in.
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
const signedSurfaces = new WeakMap<DecodedField, { isovalue: number; made: SignedSurfaces }>();

/**
 * @param field a field that is positive in some places and negative in others, such as the spin density
 * @param isovalue how far from zero the surfaces are
 * @returns a surface round where it is above the isovalue and another round where it is below minus the isovalue
 */
export function signedFieldSurfaces(field: DecodedField, isovalue: number): SignedSurfaces {
  const held = signedSurfaces.get(field);
  if (held && held.isovalue === isovalue) {
    return held.made;
  }
  // the negative part is the positive part of the field turned upside down
  const made = { positive: isosurface(field.values, field.shape, isovalue), negative: isosurface(field.values.map((value) => -value), field.shape, isovalue) };
  signedSurfaces.set(field, { isovalue, made });
  return made;
}

/**
 * Reads a field at a point.
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
