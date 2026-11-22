import {Color} from "three";
import {diverging, NEUTRAL_COLOUR} from "../../render/scale";
import type {PlaneResult} from "../session/messages";
import {autoLimit, type DecodedDensity, sampleGrid} from "./esp";
import {decodeFloats} from "./orbitals";

/** The axis a slice plane is at right angles to. */
export type SliceAxis = "x" | "y" | "z";

/** The colour of the greatest value on a scale that does not go below zero, such as the electron density. */
export const SEQUENTIAL_COLOUR = "#16508f";

/** The most samples along a side of the slice, which is more than the eye can tell apart. */
const MOST_SAMPLES = 400;

/** How many samples to take across each cell of the grid, so that the picture is smoother than the grid. */
const PER_CELL = 4;

/** How many contour lines there are on each side of zero, or for a field with no negative side. */
const CONTOUR_COUNT = 8;

/** How many steps the colour scales are cut into. */
const STEPS = 256;

/** A point in the molecule's own frame, in angstroms. */
export interface Corner {
  x: number;
  y: number;
  z: number;
}

/** A field sampled on a plane. */
export interface SliceSamples {
  /** The value at each sample, running along the first of the plane's two axes fastest; NaN where there is none. */
  values: Float32Array;
  /** How many samples there are along the first axis, and along the second. */
  width: number;
  height: number;
  /** The corners of the plane, in the order of the first axis then the second: low and low, high and low, high and high, low and high. */
  corners: [Corner, Corner, Corner, Corner];
}

/** A slice ready to draw: a picture of the field on a plane, and the lines of constant value across it. */
export interface SliceDrawing {
  /** The red, green, blue and alpha of each sample, from 0 to 255, in the order of the samples. */
  pixels: Uint8ClampedArray;
  width: number;
  height: number;
  corners: [Corner, Corner, Corner, Corner];
  /** The ends of each line segment of the contours, as pairs of points in samples; empty if there are none. */
  contours: Float32Array;
  /** The magnitude at which the colour is as strong as it gets, in electrons per cubic bohr. */
  limit: number;
}

/**
 * Samples a field on a plane that is at right angles to an axis, across the whole of the field's grid.
 *
 * @param field the field
 * @param axis the axis the plane is at right angles to
 * @param fraction how far along the field's extent in that axis the plane is, from 0 to 1
 * @returns the field on the plane
 */
export function sliceSamples(field: DecodedDensity, axis: SliceAxis, fraction: number): SliceSamples {
  const { shape } = field;
  const counts = { x: shape.nx, y: shape.ny, z: shape.nz };
  // the plane's two axes are the other two, in the order that goes round: x then y, y then z, z then x
  const across: [SliceAxis, SliceAxis] = axis === "z" ? ["x", "y"] : axis === "x" ? ["y", "z"] : ["z", "x"];
  const origin = shape.origin;
  const extent = (of: SliceAxis) => (counts[of] - 1) * shape.spacing;
  const samplesAlong = (of: SliceAxis) => Math.min(MOST_SAMPLES, (counts[of] - 1) * PER_CELL + 1);

  const width = samplesAlong(across[0]);
  const height = samplesAlong(across[1]);
  const at = origin[axis] + Math.min(1, Math.max(0, fraction)) * extent(axis);

  const place = (u: number, v: number): Corner => {
    const point: Corner = { x: 0, y: 0, z: 0 };
    point[axis] = at;
    point[across[0]] = origin[across[0]] + u * extent(across[0]);
    point[across[1]] = origin[across[1]] + v * extent(across[1]);
    return point;
  };

  const values = new Float32Array(width * height);
  for (let j = 0; j < height; j++) {
    for (let i = 0; i < width; i++) {
      const point = place(i / (width - 1), j / (height - 1));
      values[j * width + i] = sampleGrid(field.density, shape, point.x, point.y, point.z);
    }
  }
  return { values, width, height, corners: [place(0, 0), place(1, 0), place(1, 1), place(0, 1)] };
}

/** What each field's scale was worked out as. */
const limits = new WeakMap<DecodedDensity, Map<boolean, number>>();

/**
 * @param field the field
 * @param signed whether the field is positive in some places and negative in others, so the scale goes either side of zero
 * @returns how far from zero the colours reach
 */
export function sliceLimit(field: DecodedDensity, signed: boolean): number {
  let held = limits.get(field);
  if (!held) {
    held = new Map();
    limits.set(field, held);
  }
  const known = held.get(signed);
  if (known !== undefined) {
    return known;
  }

  const sizes = Float32Array.from(field.density, (value) => (Number.isFinite(value) ? Math.abs(value) : 0)).sort();
  const limit = Math.max(signed ? 1.0e-4 : 1.0e-3, sizes[Math.min(sizes.length - 1, Math.floor(sizes.length * 0.99))] ?? 0);
  held.set(signed, limit);
  return limit;
}

/** The scales as a table of red, green and blue. */
const SIGNED_TABLE = Array.from({ length: 2 * STEPS + 1 }, (_, i) => new Color(diverging((i - STEPS) / STEPS, 1)));
const SEQUENTIAL_TABLE = Array.from({ length: STEPS + 1 }, (_, i) => new Color(NEUTRAL_COLOUR).lerp(new Color(SEQUENTIAL_COLOUR), i / STEPS));

/**
 * Colours the samples of a slice. A field that is never negative is shown on a scale from near white to deep blue that rises faster at
 * the low end, so that the tails of the density show, and one that has both signs on the scale of blue above zero and red below.
 *
 * @param values the samples
 * @param signed whether the field has both signs
 * @param limit the magnitude at which the colour is as strong as it gets
 * @returns the red, green, blue and alpha of each sample, from 0 to 255, which is clear where there is no value
 */
export function sliceColours(values: ArrayLike<number>, signed: boolean, limit: number): Uint8ClampedArray {
  const pixels = new Uint8ClampedArray(values.length * 4);
  for (let at = 0; at < values.length; at++) {
    const value = values[at] as number;
    if (Number.isNaN(value) || !(limit > 0)) {
      continue;
    }
    const colour = signed
      ? (SIGNED_TABLE[Math.round((Math.max(-1, Math.min(1, value / limit)) + 1) * STEPS)] as Color)
      : (SEQUENTIAL_TABLE[Math.round(Math.sqrt(Math.max(0, Math.min(1, value / limit))) * STEPS)] as Color);
    pixels[at * 4] = Math.round(colour.r * 255);
    pixels[at * 4 + 1] = Math.round(colour.g * 255);
    pixels[at * 4 + 2] = Math.round(colour.b * 255);
    pixels[at * 4 + 3] = 255;
  }
  return pixels;
}

/**
 * @param signed whether the field has both signs
 * @param limit the magnitude at which the colour is as strong as it gets
 * @returns the values the contour lines are at: evenly spaced either side of zero for a field with both signs, and for one that is never
 *     negative closer together at the low end, to match the colours
 */
export function contourLevels(signed: boolean, limit: number): number[] {
  const levels: number[] = [];
  for (let step = 1; step <= CONTOUR_COUNT; step++) {
    const level = signed ? (limit * step) / CONTOUR_COUNT : limit * (step / CONTOUR_COUNT) ** 2;
    levels.push(level);
    if (signed) {
      levels.push(-level);
    }
  }
  return levels;
}

/**
 * Finds the lines along which a field has each of some values, by marching squares: each square of four neighbouring samples that the
 * value passes through is crossed by a segment, from one edge to another.
 *
 * @param values the samples, running along the first axis fastest
 * @param width how many there are along the first axis
 * @param height how many along the second
 * @param levels the values to find lines at
 * @returns the segments as four numbers each, the position along the first axis and the second at one end and then the other, in samples
 *     and so between 0 and `width - 1`, and 0 and `height - 1`
 */
export function contourSegments(values: ArrayLike<number>, width: number, height: number, levels: readonly number[]): Float32Array {
  const segments: number[] = [];
  for (const level of levels) {
    for (let j = 0; j + 1 < height; j++) {
      for (let i = 0; i + 1 < width; i++) {
        const a = values[j * width + i] as number;
        const b = values[j * width + i + 1] as number;
        const c = values[(j + 1) * width + i + 1] as number;
        const d = values[(j + 1) * width + i] as number;
        if (Number.isNaN(a + b + c + d)) {
          continue;
        }

        // the corners at or above the level, as bits: a is the low corner on both axes, then round the square
        const pattern = (a >= level ? 1 : 0) | (b >= level ? 2 : 0) | (c >= level ? 4 : 0) | (d >= level ? 8 : 0);
        if (pattern === 0 || pattern === 15) {
          continue;
        }

        const along = (from: number, to: number) => (level - from) / (to - from);
        // where the line crosses each edge: the bottom (a to b), the right (b to c), the top (d to c) and the left (a to d)
        const edges: [number, number][] = [
          [i + along(a, b), j],
          [i + 1, j + along(b, c)],
          [i + along(d, c), j + 1],
          [i, j + along(a, d)],
        ];
        for (const [from, to] of JOINS[pattern] as readonly (readonly [number, number])[]) {
          segments.push(...(edges[from] as [number, number]), ...(edges[to] as [number, number]));
        }
      }
    }
  }
  return Float32Array.from(segments);
}

/** For each pattern of corners at or above the level, which pairs of edges a segment joins; the two the saddles could be drawn as are drawn as separate corners. */
const JOINS: readonly (readonly (readonly [number, number])[])[] = [
  [],
  [[3, 0]],
  [[0, 1]],
  [[3, 1]],
  [[1, 2]],
  [
    [3, 0],
    [1, 2],
  ],
  [[0, 2]],
  [[3, 2]],
  [[2, 3]],
  [[0, 2]],
  [
    [0, 1],
    [2, 3],
  ],
  [[1, 2]],
  [[3, 1]],
  [[0, 1]],
  [[3, 0]],
  [],
];

/**
 * Makes a drawing of a field on a plane.
 *
 * @param field the field
 * @param axis the axis the plane is at right angles to
 * @param fraction how far along the field's extent in that axis the plane is, from 0 to 1
 * @param signed whether the field has both signs, as the spin density and the difference density do
 * @param contours whether to find the lines of constant value across the plane
 * @returns the drawing
 */
export function sliceDrawing(field: DecodedDensity, axis: SliceAxis, fraction: number, signed: boolean, contours: boolean): SliceDrawing {
  const samples = sliceSamples(field, axis, fraction);
  const limit = sliceLimit(field, signed);
  return {
    pixels: sliceColours(samples.values, signed, limit),
    width: samples.width,
    height: samples.height,
    corners: samples.corners,
    contours: contours ? contourSegments(samples.values, samples.width, samples.height, contourLevels(signed, limit)) : new Float32Array(0),
    limit,
  };
}

/** The electrostatic potential on a plane, decoded. */
export interface DecodedPlane extends SliceSamples {
  /** The axis the plane is at right angles to. */
  axis: SliceAxis;
}

/** What each result decoded to. */
const decodedPlanes = new WeakMap<PlaneResult, DecodedPlane | null>();

/**
 * @param result what the server said of the potential on a plane
 * @returns the potential at each point and where the plane is, or null if there is none or it is not the size the plane says
 */
export function decodePlane(result: PlaneResult): DecodedPlane | null {
  if (!decodedPlanes.has(result)) {
    const values = result.status === "complete" && result.potential !== null && result.corners?.length === 4 ? decodeFloats(result.potential, result.width * result.height) : null;
    decodedPlanes.set(result, values && result.corners ? { values, width: result.width, height: result.height, corners: result.corners as DecodedPlane["corners"], axis: result.axis } : null);
  }
  return decodedPlanes.get(result) ?? null;
}

/**
 * Makes a drawing of the potential on a plane. The potential has both signs, so it is coloured red below zero and blue above, as the
 * potential on a surface is.
 *
 * @param plane the potential on the plane
 * @param contours whether to find the lines of constant value across the plane
 * @returns the drawing, whose limit is in hartree per elementary charge
 */
export function planeDrawing(plane: DecodedPlane, contours: boolean): SliceDrawing {
  const limit = autoLimit(plane.values);
  return {
    pixels: sliceColours(plane.values, true, limit),
    width: plane.width,
    height: plane.height,
    corners: plane.corners,
    contours: contours ? contourSegments(plane.values, plane.width, plane.height, contourLevels(true, limit)) : new Float32Array(0),
    limit,
  };
}

/**
 * @param plane the potential on a plane
 * @param x a point, in the molecule's frame
 * @param y a point
 * @param z a point
 * @returns the potential there, found between the points of the plane, or NaN if the point is not on the plane's rectangle
 */
export function planeValueAt(plane: DecodedPlane, x: number, y: number, z: number): number {
  const [low, along, , up] = plane.corners;
  const across = (to: Corner) => ({ x: to.x - low.x, y: to.y - low.y, z: to.z - low.z });
  const [u, v] = [across(along), across(up)];
  const at = { x: x - low.x, y: y - low.y, z: z - low.z };
  const fraction = (axis: Corner) => (at.x * axis.x + at.y * axis.y + at.z * axis.z) / (axis.x ** 2 + axis.y ** 2 + axis.z ** 2);
  const [fu, fv] = [fraction(u), fraction(v)];
  // a point a little off the plane is taken to be on it, but not one off the edge of it
  if (!(fu >= 0 && fu <= 1 && fv >= 0 && fv <= 1)) {
    return Number.NaN;
  }

  const [column, row] = [fu * (plane.width - 1), fv * (plane.height - 1)];
  const [i, j] = [Math.min(Math.floor(column), plane.width - 2), Math.min(Math.floor(row), plane.height - 2)];
  const [di, dj] = [column - i, row - j];
  const at4 = (a: number, b: number) => plane.values[b * plane.width + a] as number;
  return (1 - dj) * ((1 - di) * at4(i, j) + di * at4(i + 1, j)) + dj * ((1 - di) * at4(i, j + 1) + di * at4(i + 1, j + 1));
}

/**
 * @returns a CSS gradient from the colour of zero to the strongest, to draw a legend with for a field that is never negative
 */
export function sequentialGradient(): string {
  return `linear-gradient(to right, ${NEUTRAL_COLOUR}, ${SEQUENTIAL_COLOUR})`;
}
