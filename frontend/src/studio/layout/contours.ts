import type {Surface} from "../../render/isosurface";

/** Lines across a surface. */
export interface ContourLines {
  /** Six numbers a segment: the position of one end and then of the other. */
  positions: Float32Array;
  /** Six numbers a segment: the red, green and blue at each end, from 0 to 1; or null if the surface has no colour at each vertex. */
  colours: Float32Array | null;
}

/**
 * Traces a surface as the lines where it crosses planes at right angles to each axis.
 *
 * @param surface the surface, as triangles that do not share vertices
 * @param spacing the distance between the planes, in the units of the surface
 * @param colours the red, green and blue of each vertex of the surface, from 0 to 1, to carry along the lines, or null for none
 * @returns the segments of the lines
 */
export function contourLines(surface: Surface, spacing: number, colours: Float32Array | null = null): ContourLines {
  const { positions } = surface;
  const points: number[] = [];
  const tints: number[] = [];

  for (let triangle = 0; triangle + 8 < positions.length; triangle += 9) {
    for (let axis = 0; axis < 3; axis++) {
      const along = [0, 1, 2].map((corner) => positions[triangle + corner * 3 + axis] as number);
      const first = Math.ceil(Math.min(...along) / spacing);
      const last = Math.floor(Math.max(...along) / spacing);

      for (let plane = first; plane <= last; plane++) {
        const level = plane * spacing;
        // where each edge that crosses the plane crosses it; a triangle that crosses it is crossed on two of its three edges
        const crossings: { at: number[]; tint: number[] }[] = [];
        for (let edge = 0; edge < 3; edge++) {
          const [a, b] = [edge, (edge + 1) % 3];
          const [ca, cb] = [along[a] as number, along[b] as number];
          if (ca < level === cb < level) {
            continue;
          }
          const t = (level - ca) / (cb - ca);
          const at = [0, 1, 2].map((dimension) => lerp(positions[triangle + a * 3 + dimension] as number, positions[triangle + b * 3 + dimension] as number, t));
          const tint = colours ? [0, 1, 2].map((channel) => lerp(colours[triangle + a * 3 + channel] as number, colours[triangle + b * 3 + channel] as number, t)) : [];
          crossings.push({ at, tint });
        }
        if (crossings.length === 2) {
          points.push(...(crossings[0] as { at: number[] }).at, ...(crossings[1] as { at: number[] }).at);
          if (colours) {
            tints.push(...(crossings[0] as { tint: number[] }).tint, ...(crossings[1] as { tint: number[] }).tint);
          }
        }
      }
    }
  }
  return { positions: Float32Array.from(points), colours: colours ? Float32Array.from(tints) : null };
}

/**
 * @param from a number
 * @param to another
 * @param t how far between them, from 0 to 1
 * @returns the number that far between
 */
function lerp(from: number, to: number, t: number): number {
  return from + (to - from) * t;
}
