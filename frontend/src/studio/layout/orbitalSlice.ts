import {Color} from "three";
import type {LevelBy, OrbitalColouring, OrbitalCombine, OrbitalDisplay} from "../models/OrbitalModel";
import {NEUTRAL_COLOUR as SCALE_NEUTRAL} from "../../render/scale";
import {sampleGrid} from "./esp";
import {characterColour, energyColour, isRole, roleColour} from "./orbitalColours";
import {amplitudeForShare, type DrawnOrbital, massLevel, nearestToGap} from "./orbitals";
import {
    contourSegments,
    orbitalSliceField,
    type SliceAxis,
    sliceColours,
    type SliceDrawing,
    sliceSamples
} from "./slice";

/** How a slice of orbitals is made, which is as the Orbitals tab says its surfaces are. */
export interface OrbitalSliceOptions {
  axis: SliceAxis;
  /** How far along the grid's extent in that axis the plane is, from 0 to 1. */
  fraction: number;
  /** Whether to find lines of constant value across the plane. */
  contours: boolean;
  /** Whether it shows the amplitude, which has a sign, or the probability, which does not. */
  display: OrbitalDisplay;
  /** What colours it. */
  colouring: OrbitalColouring;
  /** How several orbitals are shown: one on its own, the sum of their probabilities, or whichever is largest at each point. */
  combine: OrbitalCombine;
  /** Whether the level of an orbital, the value that is drawn at one, is its isovalue or the amplitude that holds a share of it. */
  levelBy: LevelBy;
  isovalue: number;
  /** How much of an orbital its level holds, as a percentage, where the level is chosen by share, and always for the probability. */
  share: number;
  /** Which of the orbitals is the one shown on its own, or whose grid and side of the gap are used where they are merged. */
  pick: number;
}

/** A value read from a slice at a point. */
export interface SliceReading {
  value: number;
  unit: string;
  name: string;
}

/** How much of the colour an orbital has at its level: half strength, so that what is stronger than its surface is stronger still. */
const STRENGTH_AT_LEVEL = 0.5;

/** The most a slice of the amplitude is coloured to, in multiples of the level, which is where the sign colours are as strong as they get. */
const SIGN_LIMIT = 2;

/** The most a slice of a probability is coloured to, in multiples of its level, which is where the single colour is as strong as it gets. */
const PROBABILITY_LIMIT = 4;

/** The values of the lines drawn across a slice, in multiples of the level: the level itself, which is where the surface is, and either side of it. */
const AMPLITUDE_LINES = [0.5, 1, 2];
const PROBABILITY_LINES = [0.25, 1, 4];

/** The colour of a pixel that has none of an orbital's colour. */
const PALE = new Color(SCALE_NEUTRAL);

/**
 * @param shape where a grid is
 * @returns a string that is the same for grids in the same place with the same points
 */
function gridKey(shape: DrawnOrbital["field"]["shape"]): string {
  return `${shape.nx},${shape.ny},${shape.nz},${shape.spacing},${shape.origin.x},${shape.origin.y},${shape.origin.z}`;
}

/**
 * Makes a drawing of orbitals on a plane, as their surfaces are made in the scene: the same level, the same colours and the same way of
 * showing several, so that a line across the plane at the level is where the surface cuts it.
 *
 * @param orbitals the orbitals to show, with their values on grids
 * @param options how to show them
 * @returns the drawing, with how to read a value from it, or null if there are no orbitals
 */
export function orbitalSliceDrawing(orbitals: readonly DrawnOrbital[], options: OrbitalSliceOptions): (SliceDrawing & { read: (x: number, y: number, z: number) => SliceReading | null }) | null {
  if (orbitals.length === 0) {
    return null;
  }

  const anchor = orbitals[Math.max(0, Math.min(options.pick, orbitals.length - 1))] as DrawnOrbital;
  const key = gridKey(anchor.field.shape);
  const probability = options.display === "probability";
  const merged = orbitals.length > 1 && options.combine !== "separate";

  // the orbitals that are shown: the one on its own, or those that share its grid; a sum does not mix the filled with the empty
  const shown = merged ? orbitals.filter((each) => gridKey(each.field.shape) === key && (options.combine === "union" || each.occupied === anchor.occupied)) : [anchor];
  // the level of each orbital is the value drawn at its surface: the amplitude that holds the share, or the isovalue
  const levels = shown.map((each) => (probability || options.levelBy === "share" ? amplitudeForShare(each.field.values, options.share / 100) : options.isovalue));
  const members = shown.flatMap((orbital, at) => ((levels[at] as number) > 0 ? [{ orbital, level: levels[at] as number }] : []));
  if (members.length === 0) {
    return null;
  }

  const sampled = members.map(({ orbital }) => sliceSamples(orbitalSliceField([orbital], 0, false)!, options.axis, options.fraction));
  const { width, height, corners } = sampled[0] as ReturnType<typeof sliceSamples>;
  const count = width * height;

  const intensity = new Float32Array(count);
  const signs = new Int8Array(count);
  const owners = new Int32Array(count);
  let sumField: { values: Float32Array; level: number; shape: DrawnOrbital["field"]["shape"] } | null = null;

  if (merged && options.combine === "sum") {
    // the sum of the probabilities of the orbitals, each as many times as it holds electrons, on the whole grid, with the level that holds the share
    const sum = new Float32Array(members[0]!.orbital.field.values.length);
    for (const { orbital } of members) {
      for (let point = 0; point < sum.length; point++) {
        const value = orbital.field.values[point] as number;
        sum[point] = (sum[point] as number) + orbital.weight * value * value;
      }
    }
    const level = massLevel(sum, options.share / 100);
    if (!(level > 0)) {
      return null;
    }
    sumField = { values: sum, level, shape: members[0]!.orbital.field.shape };
    const over = sliceSamples({ density: sum, shape: sumField.shape }, options.axis, options.fraction);
    const nearest = nearestToGap(members.map(({ orbital }) => orbital));
    for (let at = 0; at < count; at++) {
      intensity[at] = (over.values[at] as number) / level;
      owners[at] = nearest;
    }
  } else {
    for (let at = 0; at < count; at++) {
      let best = 0;
      let largest = Number.NEGATIVE_INFINITY;
      members.forEach(({ level }, member) => {
        const scaled = Math.abs((sampled[member] as ReturnType<typeof sliceSamples>).values[at] as number) / level;
        if (scaled > largest) {
          largest = scaled;
          best = member;
        }
      });
      const here = (sampled[best] as ReturnType<typeof sliceSamples>).values[at] as number;
      owners[at] = best;
      signs[at] = here >= 0 ? 1 : -1;
      // the probability at the level is one, so what is shown is the square of how many times the level the amplitude is
      intensity[at] = Number.isNaN(largest) ? Number.NaN : probability ? largest * largest : largest;
    }
  }

  const bySign = options.colouring === "phase" && !probability && !(merged && options.combine === "sum");
  const signed = bySign ? Float32Array.from(intensity, (value, at) => value * (signs[at] as number)) : null;

  let pixels: Uint8ClampedArray;
  if (options.colouring === "phase") {
    pixels = signed ? sliceColours(signed, true, SIGN_LIMIT) : sliceColours(intensity, false, PROBABILITY_LIMIT);
  } else {
    pixels = new Uint8ClampedArray(count * 4);
    const tints = members.map(({ orbital }) => new Color(isRole(options.colouring) ? roleColour(options.colouring, orbital.offset, orbital.spin) : options.colouring === "energy" ? energyColour(orbital.occupied, orbital.fromGapEv) : characterColour(orbital.pi)));
    const colour = new Color();
    for (let at = 0; at < count; at++) {
      const value = intensity[at] as number;
      if (Number.isNaN(value)) {
        continue;
      }
      // the colour of the orbital that is largest here, as strong as the orbital is at this point
      const strength = Math.min(1, (value * STRENGTH_AT_LEVEL) / 1);
      colour.copy(PALE).lerp(tints[owners[at] as number] as Color, strength);
      pixels[at * 4] = Math.round(colour.r * 255);
      pixels[at * 4 + 1] = Math.round(colour.g * 255);
      pixels[at * 4 + 2] = Math.round(colour.b * 255);
      pixels[at * 4 + 3] = 255;
    }
  }

  const lines = probability || merged && options.combine === "sum" ? PROBABILITY_LINES : AMPLITUDE_LINES;
  const contourLevels = signed ? [...lines, ...lines.map((value) => -value)] : lines;
  const contours = options.contours ? contourSegments(signed ?? intensity, width, height, contourLevels) : new Float32Array(0);

  const amplitude = !probability;
  const read = (x: number, y: number, z: number): SliceReading | null => {
    if (sumField) {
      const value = sampleGrid(sumField.values, sumField.shape, x, y, z);
      return Number.isNaN(value) ? null : { value, unit: "e/bohr³", name: "summed probability" };
    }
    let best = 0;
    let largest = Number.NEGATIVE_INFINITY;
    members.forEach(({ orbital, level }, member) => {
      const scaled = Math.abs(sampleGrid(orbital.field.values, orbital.field.shape, x, y, z)) / level;
      if (scaled > largest) {
        largest = scaled;
        best = member;
      }
    });
    const owner = members[best]!.orbital;
    const value = sampleGrid(owner.field.values, owner.field.shape, x, y, z);
    if (Number.isNaN(value)) {
      return null;
    }
    return amplitude ? { value, unit: "bohr⁻³ᐟ²", name: "orbital amplitude" } : { value: value * value, unit: "e/bohr³", name: "orbital probability" };
  };

  return { pixels, width, height, corners, contours, limit: SIGN_LIMIT, read };
}
