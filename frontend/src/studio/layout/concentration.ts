import type {Surface} from "../../render/isosurface";
import {type DecodedDensity, densitySurface, sampleGrid, vertexColours} from "./esp";
import {strongest} from "./reactivity";

/** The colour of a surface of the electron localisation function, which is violet so that it is not taken for a density or an orbital. */
export const ELF_COLOUR = "#8f6bd6";

/** A surface of the electron density, with minus the Laplacian of the density at each of its vertices. */
export interface ConcentrationSurface {
  surface: Surface;
  /** Minus the Laplacian at each vertex, in electrons per fifth power of a bohr: positive where charge is concentrated, negative where it is depleted. */
  values: Float32Array;
  /** The value the colours are centred on, which is the median of the vertices, so that the colours show which parts are more and which less concentrated than the rest. */
  centre: number;
  /** How far from the centre the colours are as strong as they get, which a few spikes beyond do not move. */
  limit: number;
}

/** The last surface made of each density, which is kept for as long as the level and the field it was made from are asked for. */
const held = new WeakMap<DecodedDensity, { isovalue: number; field: DecodedDensity; made: ConcentrationSurface }>();

/**
 * Finds the surface of the electron density at a value and minus the Laplacian of the density on it: charge-depleted holes, such as where a
 * halogen's sigma-hole is, come out negative, and lone pairs and bonds positive.
 *
 * @param density the electron density
 * @param isovalue the density the surface is at, in electrons per cubic bohr
 * @param field minus the Laplacian of the density on a grid, which need not be the grid of the density
 * @returns the surface, the field at each of its vertices, and how far the colours should reach
 */
export function concentrationSurface(density: DecodedDensity, isovalue: number, field: DecodedDensity): ConcentrationSurface {
  const kept = held.get(density);
  if (kept && kept.isovalue === isovalue && kept.field === field) {
    return kept.made;
  }

  const surface = densitySurface(density, isovalue);
  const values = new Float32Array(surface.positions.length / 3);
  for (let v = 0; v < values.length; v++) {
    values[v] = sampleGrid(field.density, field.shape, surface.positions[v * 3] as number, surface.positions[v * 3 + 1] as number, surface.positions[v * 3 + 2] as number);
  }

  // on the outer surface of a molecule the density is always falling away, so charge is depleted nearly everywhere and a scale about zero would be all
  // one colour. The scale is centred on the median instead, and a few spikes do not wash out the rest
  const found = Array.from(values).filter((value) => !Number.isNaN(value));
  const centre = median(found);
  const made = { surface, values, centre, limit: strongest(found.map((value) => Math.abs(value - centre))) };
  held.set(density, { isovalue, field, made });
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
 * @returns the red, green and blue of each vertex, from 0 to 1, as three numbers a vertex: blue where charge is more concentrated than at the middle of
 *     the surface and red where it is less
 */
export function concentrationColours(made: ConcentrationSurface): Float32Array {
  return vertexColours(Float32Array.from(made.values, (value) => value - made.centre), made.limit);
}
