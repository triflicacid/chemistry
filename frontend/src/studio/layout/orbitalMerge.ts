import {type GridShape, isosurface, type Surface} from "../../render/isosurface";
import type {LevelBy, OrbitalColouring} from "../models/OrbitalModel";
import {sampleGrid} from "./gridField";
import {amplitudeForShare, type DrawnOrbital, massLevel, nearestToGap} from "./orbitals";
import {
    characterColour,
    CORE_SHELL,
    EMPTY_CORE_SHELL,
    EMPTY_OUTER_SHELL,
    energyColour,
    isRole,
    NEGATIVE_LOBE,
    OUTER_SHELL,
    POSITIVE_LOBE,
    roleColour
} from "./orbitalColours";

/** One surface of several orbitals merged, with its colour. */
export interface MergedPart {
  surface: Surface;
  /** The red, green and blue of each vertex, from 0 to 1, or null to draw the whole surface in {@link colour}. */
  colours: Float32Array | null;
  /** The colour of a surface with none for each vertex. */
  colour: string;
  /** How opaque it is. */
  opacity: number;
}

/** How the surface of the union of several orbitals is drawn. */
export interface UnionOptions {
  /** Whether each orbital's level is the isovalue or the amplitude that holds the share. */
  levelBy: LevelBy;
  /** The value of an orbital at its surface where the level is chosen by isovalue. */
  isovalue: number;
  /** How much of each orbital its part of the surface holds, as a percentage, where the level is chosen by share. */
  share: number;
  /** What colours the surface: the sign of the orbital that is largest at each part, the orbital that is, what that orbital is made of, or its energy. */
  colouring: OrbitalColouring;
  /** How opaque the surface is. */
  opacity: number;
}

/** How opaque the outer shell is next to the core. */
const OUTER_OPACITY = 0.55;

/**
 * @param orbital an orbital to draw
 * @returns a string that is the same for orbitals that are in the same place with the same points and are both filled or both empty
 */
function groupKey(orbital: DrawnOrbital): string {
  return `${gridKey(orbital.field.shape)}|${orbital.occupied}`;
}

/**
 * @param shape where a grid is
 * @returns a string that is the same for grids that are in the same place with the same points
 */
function gridKey(shape: GridShape): string {
  return `${shape.nx},${shape.ny},${shape.nz},${shape.spacing},${shape.origin.x},${shape.origin.y},${shape.origin.z}`;
}

/**
 * @param orbitals the orbitals to draw
 * @returns the positions in that list of those that share a grid, for each grid, since orbitals can only be merged where their values are at the same
 *     points, and of those that are filled and those that are empty, which are not merged with one another, since the one is where electrons are
 *     and the other where they would go
 */
function byGrid(orbitals: readonly DrawnOrbital[]): number[][] {
  const groups = new Map<string, number[]>();
  orbitals.forEach((orbital, at) => {
    const key = groupKey(orbital);
    groups.set(key, [...(groups.get(key) ?? []), at]);
  });
  return [...groups.values()];
}

/**
 * Merges orbitals into one probability: the sum of their squared amplitudes, each as many times as it holds electrons, so that the filled
 * orbitals together are the electron density of those orbitals. It is drawn as two shells, one holding half of it and the other the share
 * asked for, which show where the electrons are most likely to be.
 *
 * @param orbitals the orbitals to merge, which are merged in groups that share a grid
 * @param share how much of the whole the outer shell holds, as a percentage
 * @param opacity how opaque the core is, and the outer shell a part of that
 * @param colouring what colours the shells: the energy of the orbitals, the mean of the group; or the type or class of the one nearest the gap; and
 *     otherwise whether they are filled
 * @returns the shells
 */
export function mergeSummed(orbitals: readonly DrawnOrbital[], share: number, opacity: number, colouring: OrbitalColouring = "phase"): MergedPart[] {
  const parts: MergedPart[] = [];
  for (const group of byGrid(orbitals)) {
    const { shape } = (orbitals[group[0] as number] as DrawnOrbital).field;
    const sum = new Float32Array((orbitals[group[0] as number] as DrawnOrbital).field.values.length);
    for (const at of group) {
      const { field, weight } = orbitals[at] as DrawnOrbital;
      for (let point = 0; point < sum.length; point++) {
        const value = field.values[point] as number;
        sum[point] = (sum[point] as number) + weight * value * value;
      }
    }

    // the shells of the filled orbitals are warm, and of the empty cool, since they are where electrons are and where they would go
    const first = orbitals[group[0] as number] as DrawnOrbital;
    const meanFromGap = group.reduce((sum, at) => sum + (orbitals[at] as DrawnOrbital).fromGapEv, 0) / group.length;
    const nearest = orbitals[group[nearestToGap(group.map((at) => orbitals[at] as DrawnOrbital))] as number] as DrawnOrbital;
    const [core, outer] =
      colouring === "energy"
        ? [energyColour(first.occupied, meanFromGap), energyColour(first.occupied, meanFromGap)]
        : isRole(colouring)
          ? [roleColour(colouring, nearest.offset, nearest.spin), roleColour(colouring, nearest.offset, nearest.spin)]
          : first.occupied
        ? [CORE_SHELL, OUTER_SHELL]
        : [EMPTY_CORE_SHELL, EMPTY_OUTER_SHELL];
    const shells = share > 50 ? [{ share: 50, colour: core, opacity }, { share, colour: outer, opacity: opacity * OUTER_OPACITY }] : [{ share, colour: core, opacity }];
    for (const shell of shells) {
      const level = massLevel(sum, shell.share / 100);
      if (level > 0) {
        parts.push({ surface: isosurface(sum, shape, level), colours: null, colour: shell.colour, opacity: Math.min(1, shell.opacity) });
      }
    }
  }
  return parts;
}

/**
 * Merges orbitals into one surface that is wherever any of them reaches its own level. Each is first divided by its own level, so that a
 * diffuse orbital counts as much as a compact one, and the surface is where the largest of them is one. Each part of it is coloured as the
 * orbital that is largest there, by its sign where the colouring is by phase, so that the lobes keep their blue and red, or by its type or energy or
 * what it is made of. Where lobes of opposite sign meet they are one surface with a blue part and a red part.
 *
 * @param orbitals the orbitals to merge, which are merged in groups that share a grid; the hue of each is by its place in this list
 * @param options how to choose the levels and colour the surface
 * @returns the surface of each group
 */
export function mergeUnion(orbitals: readonly DrawnOrbital[], options: UnionOptions): MergedPart[] {
  const parts: MergedPart[] = [];
  for (const group of byGrid(orbitals)) {
    const { shape, values } = (orbitals[group[0] as number] as DrawnOrbital).field;

    // each orbital's amplitude as a multiple of its own level, which is where it reaches the surface at one
    const members = group.flatMap((at) => {
      const { field, pi } = orbitals[at] as DrawnOrbital;
      const level = options.levelBy === "share" ? amplitudeForShare(field.values, options.share / 100) : options.isovalue;
      return level > 0 ? [{ at, pi, signed: field.values, scaled: Float32Array.from(field.values, (value) => Math.abs(value) / level) }] : [];
    });
    if (members.length === 0) {
      continue;
    }

    const union = new Float32Array(values.length);
    for (const { scaled } of members) {
      for (let point = 0; point < union.length; point++) {
        union[point] = Math.max(union[point] as number, scaled[point] as number);
      }
    }

    const surface = isosurface(union, shape, 1);
    const colours = new Float32Array(surface.positions.length);
    // by sign the colour is found at each vertex, from the orbital that is largest there; otherwise it belongs to that orbital throughout
    const tints = members.map(({ at, pi }) => {
      const orbital = orbitals[at] as DrawnOrbital;
      const colour =
        options.colouring === "character"
          ? characterColour(pi)
          : options.colouring === "energy"
            ? energyColour(orbital.occupied, orbital.fromGapEv)
            : isRole(options.colouring)
              ? roleColour(options.colouring, orbital.offset, orbital.spin)
              : NEGATIVE_LOBE;
      return hexToUnit(colour);
    });
    const positive = hexToUnit(POSITIVE_LOBE);
    const negative = hexToUnit(NEGATIVE_LOBE);
    for (let vertex = 0; vertex < surface.positions.length; vertex += 3) {
      const [x, y, z] = [surface.positions[vertex] as number, surface.positions[vertex + 1] as number, surface.positions[vertex + 2] as number];
      let owner = 0;
      let largest = Number.NEGATIVE_INFINITY;
      members.forEach(({ scaled }, member) => {
        const here = sampleGrid(scaled, shape, x, y, z);
        if (here > largest) {
          largest = here;
          owner = member;
        }
      });
      const tint =
        options.colouring === "phase" ? (sampleGrid((members[owner] as (typeof members)[number]).signed, shape, x, y, z) >= 0 ? positive : negative) : (tints[owner] as [number, number, number]);
      colours[vertex] = tint[0];
      colours[vertex + 1] = tint[1];
      colours[vertex + 2] = tint[2];
    }
    parts.push({ surface, colours, colour: POSITIVE_LOBE, opacity: options.opacity });
  }
  return parts;
}

/**
 * @param hex a colour as `#rrggbb`
 * @returns its red, green and blue, each from 0 to 1
 */
function hexToUnit(hex: string): [number, number, number] {
  return [1, 3, 5].map((at) => parseInt(hex.slice(at, at + 2), 16) / 255) as [number, number, number];
}
