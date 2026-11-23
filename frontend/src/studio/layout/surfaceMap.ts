import type {Surface} from "../../render/isosurface";
import {vertexColours} from "./esp";
import {fieldSurface, sampleGrid} from "./gridField";
import type {DecodedField} from "./orbitals";
import {strongest} from "./reactivity";

/** The colour of a surface of the electron localisation function, which is violet so that it is not taken for a density or an orbital. */
export const ELF_COLOUR = "#8f6bd6";

/**
 * What the colours of a mapped field are centred on: the median of the vertices, for a field that is nearly all one sign on the surface, so that the
 * colours show which parts are more and which less than the rest, or zero, for one whose sign means something, such as what is gained and what is lost.
 */
export type MapCentre = "median" | "zero";

/** A surface of the electron density, with another field read at each of its vertices. */
export interface FieldOnSurface {
  surface: Surface;
  /** The field at each vertex, in its own unit, which is NaN where there was none to read. */
  values: Float32Array;
  /** The value the colours are centred on. */
  centre: number;
  /** How far from the centre the colours are as strong as they get, which a few spikes beyond do not move. */
  limit: number;
}

/** The last surface made of each density, which is kept for as long as what it was made from is asked for again. */
const held = new WeakMap<DecodedField, { isovalue: number; field: DecodedField; about: MapCentre; made: FieldOnSurface }>();

/**
 * Finds the surface of the electron density at a value and another field read on it, such as minus the Laplacian, whose holes and lone pairs then
 * show on the shape of the molecule, or what bonding gains and loses.
 *
 * @param density the electron density
 * @param isovalue the density the surface is at, in electrons per cubic bohr
 * @param field the field to read on it, on a grid that need not be the grid of the density
 * @param about what the colours are centred on
 * @returns the surface, the field at each of its vertices, and how far the colours should reach
 */
export function fieldOnSurface(density: DecodedField, isovalue: number, field: DecodedField, about: MapCentre): FieldOnSurface {
  const kept = held.get(density);
  if (kept && kept.isovalue === isovalue && kept.field === field && kept.about === about) {
    return kept.made;
  }

  const surface = fieldSurface(density, isovalue);
  const values = new Float32Array(surface.positions.length / 3);
  for (let v = 0; v < values.length; v++) {
    values[v] = sampleGrid(field.values, field.shape, surface.positions[v * 3] as number, surface.positions[v * 3 + 1] as number, surface.positions[v * 3 + 2] as number);
  }

  // on the outer surface of a molecule the density is always falling away, so a field such as the Laplacian is one sign nearly everywhere and a scale
  // about zero would be all one colour. Where the sign means something it is kept, and a few spikes do not wash the rest out either way
  const found = Array.from(values).filter((value) => !Number.isNaN(value));
  const centre = about === "zero" ? 0 : median(found);
  const made = { surface, values, centre, limit: strongest(found.map((value) => Math.abs(value - centre))) };
  held.set(density, { isovalue, field, about, made });
  return made;
}

/**
 * @param numbers some numbers
 * @returns their median, or zero if there are none
 */
function median(numbers: readonly number[]): number {
  const sorted = [...numbers].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length === 0 ? 0 : sorted.length % 2 === 1 ? (sorted[middle] as number) : ((sorted[middle - 1] as number) + (sorted[middle] as number)) / 2;
}

/**
 * @param made the surface and the field on it
 * @returns the red, green and blue of each vertex, from 0 to 1, as three numbers a vertex: blue where the field is above the centre of the scale and
 *     red where it is below
 */
export function fieldOnSurfaceColours(made: FieldOnSurface): Float32Array {
  return vertexColours(Float32Array.from(made.values, (value) => value - made.centre), made.limit);
}
