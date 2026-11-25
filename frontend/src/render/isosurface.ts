import {edgeTable, triTable} from "three/examples/jsm/objects/MarchingCubes.js";

/** Where a regular grid of values is. */
export interface GridShape {
  /** How many points there are along x, y and z. */
  nx: number;
  ny: number;
  nz: number;
  /** The position of the point with all indices zero. */
  origin: { x: number; y: number; z: number };
  /** The distance between neighbouring points, the same along each axis. */
  spacing: number;
}

/** A surface as triangles that do not share vertices. */
export interface Surface {
  /** Three numbers a vertex, three vertices a triangle. */
  positions: Float32Array;
  /** The direction that points out of the shape at each vertex, three numbers a vertex. */
  normals: Float32Array;
}

/** The two ends of each edge of a cube, by the corners they join. The corners run round the bottom face and then the top. */
const EDGE_ENDS: readonly (readonly [number, number])[] = [
  [0, 1], [1, 2], [2, 3], [3, 0], [4, 5], [5, 6], [6, 7], [7, 4], [0, 4], [1, 5], [2, 6], [3, 7],
];

/** How far along each axis each corner of a cube is from the corner with all offsets zero. */
const CORNER: readonly (readonly [number, number, number])[] = [
  [0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0], [0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1],
];

/**
 * Finds the surface where a field passes through a level, with the field higher inside it, by marching cubes.
 *
 * @param values the field at each point of the grid, running along x fastest, then y, then z
 * @param shape where the grid is
 * @param level the value the surface is at
 * @returns the surface, with normals that point out of the part of the field that is above the level; empty if the field never
 *     reaches the level
 */
export function isosurface(values: Float32Array, shape: GridShape, level: number): Surface {
  const { nx, ny, nz, origin, spacing } = shape;
  const at = (i: number, j: number, k: number) => values[(k * ny + j) * nx + i] ?? 0;

  // the gradient of the field by central differences, one-sided at the edges of the grid
  const gradient = (i: number, j: number, k: number): [number, number, number] => [
    (at(Math.min(i + 1, nx - 1), j, k) - at(Math.max(i - 1, 0), j, k)) / ((Math.min(i + 1, nx - 1) - Math.max(i - 1, 0)) * spacing),
    (at(i, Math.min(j + 1, ny - 1), k) - at(i, Math.max(j - 1, 0), k)) / ((Math.min(j + 1, ny - 1) - Math.max(j - 1, 0)) * spacing),
    (at(i, j, Math.min(k + 1, nz - 1)) - at(i, j, Math.max(k - 1, 0))) / ((Math.min(k + 1, nz - 1) - Math.max(k - 1, 0)) * spacing),
  ];

  const positions: number[] = [];
  const normals: number[] = [];
  const corners = new Float64Array(8);
  const crossing = new Array<[number, number, number, number, number, number]>(12);

  for (let k = 0; k < nz - 1; k++) {
    for (let j = 0; j < ny - 1; j++) {
      for (let i = 0; i < nx - 1; i++) {
        let index = 0;
        for (let c = 0; c < 8; c++) {
          const [di, dj, dk] = CORNER[c] as readonly [number, number, number];
          corners[c] = at(i + di, j + dj, k + dk);
          // a corner below the level is outside, which sets its bit
          if ((corners[c] as number) < level) {
            index |= 1 << c;
          }
        }

        const edges = edgeTable[index] as number;
        if (edges === 0) {
          continue;
        }

        for (let e = 0; e < 12; e++) {
          if ((edges & (1 << e)) === 0) {
            continue;
          }
          const [a, b] = EDGE_ENDS[e] as readonly [number, number];
          const [ai, aj, ak] = CORNER[a] as readonly [number, number, number];
          const [bi, bj, bk] = CORNER[b] as readonly [number, number, number];
          const low = corners[a] as number;
          const high = corners[b] as number;
          const t = high === low ? 0.5 : (level - low) / (high - low);
          const ga = gradient(i + ai, j + aj, k + ak);
          const gb = gradient(i + bi, j + bj, k + bk);
          crossing[e] = [
            origin.x + (i + ai + t * (bi - ai)) * spacing,
            origin.y + (j + aj + t * (bj - aj)) * spacing,
            origin.z + (k + ak + t * (bk - ak)) * spacing,
            // the field is higher inside, so the way out is down the gradient
            -(ga[0] + t * (gb[0] - ga[0])),
            -(ga[1] + t * (gb[1] - ga[1])),
            -(ga[2] + t * (gb[2] - ga[2])),
          ];
        }

        for (let t = 0; (triTable[index * 16 + t] as number) !== -1; t += 3) {
          const triangle = [0, 1, 2].map((v) => crossing[triTable[index * 16 + t + v] as number] as [number, number, number, number, number, number]);
          const [p, q, r] = triangle as [(typeof triangle)[number], (typeof triangle)[number], (typeof triangle)[number]];
          emit(positions, normals, p, q, r);
        }
      }
    }
  }
  return { positions: new Float32Array(positions), normals: new Float32Array(normals) };
}

/**
 * Adds a triangle, turned if need be so that the way it faces agrees with the way its vertices' normals point.
 *
 * @param positions where to add the vertices
 * @param normals where to add their normals, normalised
 * @param p one corner, with its position and then its normal
 * @param q another
 * @param r the third
 */
function emit(
  positions: number[],
  normals: number[],
  p: [number, number, number, number, number, number],
  q: [number, number, number, number, number, number],
  r: [number, number, number, number, number, number],
): void {
  const ux = q[0] - p[0];
  const uy = q[1] - p[1];
  const uz = q[2] - p[2];
  const vx = r[0] - p[0];
  const vy = r[1] - p[1];
  const vz = r[2] - p[2];
  const facing = [uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx];
  const sum = [p[3] + q[3] + r[3], p[4] + q[4] + r[4], p[5] + q[5] + r[5]];
  const agrees = (facing[0] as number) * (sum[0] as number) + (facing[1] as number) * (sum[1] as number) + (facing[2] as number) * (sum[2] as number) >= 0;

  for (const vertex of agrees ? [p, q, r] : [p, r, q]) {
    positions.push(vertex[0], vertex[1], vertex[2]);
    const length = Math.hypot(vertex[3], vertex[4], vertex[5]) || 1;
    normals.push(vertex[3] / length, vertex[4] / length, vertex[5] / length);
  }
}
