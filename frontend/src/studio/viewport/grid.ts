import {BufferAttribute, BufferGeometry, Color, LineBasicMaterial, LineSegments} from "three";

/** A square grid of lines, or rings and spokes about a centre. */
export type GridKind = "cartesian" | "polar";

/** The plane a grid lies in, named by the two scene axes that span it. */
export type GridPlane = "xy" | "xz" | "yz";

/** A plane, or all three at once. */
export type GridPlaneChoice = GridPlane | "all";

/** How far apart the lines of a grid, or the rings of a polar one, are, in angstroms. */
export const GRID_SPACING = 1;

/** How far a grid in one plane reaches from its centre in each direction, in angstroms, past which nothing is drawn. */
export const GRID_EXTENT = 30;

/** How far each grid reaches when all three planes are drawn. */
export const GRID_ALL_EXTENT = 10;

/** Every how many lines or rings a stronger one is drawn, to give a sense of scale. */
export const GRID_MAJOR_EVERY = 5;

/** The angle between the spokes of a polar grid, in degrees. */
export const GRID_SPOKE_DEGREES = 30;

/** The planes drawn when all of them are, in the order they are drawn. */
export const ALL_PLANES: readonly GridPlane[] = ["xz", "xy", "yz"];

/** How far apart the vertices along a line are, in angstroms. A line is cut into pieces so that it can fade along its length. */
const PIECE = 2;

/** How many chords make one ring. */
const CHORDS = 72;

/** The colour of each scene axis, as the axes drawn at the origin have it. */
const AXIS_COLOUR = { x: 0xe41a1c, y: 0x2ca02c, z: 0x1f4fd8 } as const;

const GREY = 0x808080;

/** How opaque a line is at its strongest, by what it is. */
const STRENGTH = { minor: 0.22, major: 0.5, axis: 0.9 } as const;

type Axis = keyof typeof AXIS_COLOUR;

/** One short straight piece of a grid, in the grid's own plane before it is laid on the scene's. */
export interface GridSegment {
  /** Where it starts and ends, as the two coordinates that span the plane. */
  from: [number, number];
  to: [number, number];
  /** How opaque each end is, from 0 to 1. */
  fromAlpha: number;
  toAlpha: number;
  /** The colour of the line, as a hex number. */
  colour: number;
}

/**
 * @param plane which plane
 * @returns the scene axis each of the plane's two coordinates runs along
 */
export function axesOf(plane: GridPlane): [Axis, Axis] {
  return [plane[0] as Axis, plane[1] as Axis];
}

/**
 * @param choice one plane or all three
 * @returns how far the grid reaches from its centre, in angstroms
 */
export function extentOf(choice: GridPlaneChoice): number {
  return choice === "all" ? GRID_ALL_EXTENT : GRID_EXTENT;
}

/**
 * How opaque a grid is at a distance from its centre, which falls away smoothly to nothing at the extent.
 *
 * @param distance how far from the centre, in angstroms
 * @param extent how far the grid reaches
 * @returns the share of full opacity, from 1 at the centre to 0 at the edge
 */
export function fade(distance: number, extent: number = GRID_EXTENT): number {
  return Math.max(0, 1 - distance / extent) ** 1.5;
}

/**
 * Works out every piece of a grid, with no reference to drawing it.
 *
 * @param kind square or polar
 * @param plane the plane it lies in, which decides which axis colours the central lines
 * @param extent how far it reaches from the centre, in angstroms
 * @returns its pieces, which fade towards the edge
 */
export function gridSegments(kind: GridKind, plane: GridPlane, extent: number = GRID_EXTENT): GridSegment[] {
  const [first, second] = axesOf(plane);
  return kind === "cartesian"
    ? square(AXIS_COLOUR[first], AXIS_COLOUR[second], extent)
    : polar(AXIS_COLOUR[first], AXIS_COLOUR[second], extent);
}

function square(firstColour: number, secondColour: number, extent: number): GridSegment[] {
  const segments: GridSegment[] = [];
  const lines = Math.round(extent / GRID_SPACING);

  for (let i = -lines; i <= lines; i++) {
    const at = i * GRID_SPACING;
    const strength = i === 0 ? STRENGTH.axis : i % GRID_MAJOR_EVERY === 0 ? STRENGTH.major : STRENGTH.minor;

    // the line at a first coordinate of zero runs along the second axis, and the reverse
    const alongSecond = i === 0 ? secondColour : GREY;
    const alongFirst = i === 0 ? firstColour : GREY;
    for (let along = -extent; along < extent; along += PIECE) {
      const end = Math.min(along + PIECE, extent);
      segments.push(piece([at, along], [at, end], strength, alongSecond, extent));
      segments.push(piece([along, at], [end, at], strength, alongFirst, extent));
    }
  }
  return segments;
}

function polar(firstColour: number, secondColour: number, extent: number): GridSegment[] {
  const segments: GridSegment[] = [];
  const rings = Math.round(extent / GRID_SPACING);

  for (let ring = 1; ring <= rings; ring++) {
    const radius = ring * GRID_SPACING;
    const strength = ring % GRID_MAJOR_EVERY === 0 ? STRENGTH.major : STRENGTH.minor;
    for (let chord = 0; chord < CHORDS; chord++) {
      const a = (chord / CHORDS) * 2 * Math.PI;
      const b = ((chord + 1) / CHORDS) * 2 * Math.PI;
      segments.push(piece([radius * Math.cos(a), radius * Math.sin(a)], [radius * Math.cos(b), radius * Math.sin(b)], strength, GREY, extent));
    }
  }

  for (let degrees = 0; degrees < 360; degrees += GRID_SPOKE_DEGREES) {
    const angle = (degrees * Math.PI) / 180;
    const [x, y] = [Math.cos(angle), Math.sin(angle)];
    const colour = degrees === 0 ? firstColour : degrees === 90 ? secondColour : GREY;
    const strength = degrees === 0 || degrees === 90 ? STRENGTH.axis : STRENGTH.major;
    for (let along = 0; along < extent; along += PIECE) {
      const end = Math.min(along + PIECE, extent);
      segments.push(piece([x * along, y * along], [x * end, y * end], strength, colour, extent));
    }
  }
  return segments;
}

/** A piece of a line of a given strength, faded by how far each end is from the centre. */
function piece(from: [number, number], to: [number, number], strength: number, colour: number, extent: number): GridSegment {
  return {
    from,
    to,
    fromAlpha: strength * fade(Math.hypot(from[0], from[1]), extent),
    toAlpha: strength * fade(Math.hypot(to[0], to[1]), extent),
    colour,
  };
}

/**
 * Draws a grid, lying in one of the scene's planes, or in all three, about the origin.
 *
 * @param kind square or polar
 * @param choice the plane it lies in, or all three
 * @returns the lines, which the caller adds to the scene and, when done with them, disposes the geometry and material of
 */
export function buildGrid(kind: GridKind, choice: GridPlaneChoice): LineSegments {
  const extent = extentOf(choice);
  const planes = choice === "all" ? ALL_PLANES : [choice];
  const drawn = planes.flatMap((plane) => gridSegments(kind, plane, extent).map((segment) => ({ plane, segment })));

  const positions = new Float32Array(drawn.length * 6);
  const colours = new Float32Array(drawn.length * 8);

  drawn.forEach(({ plane, segment }, i) => {
    const colour = new Color(segment.colour);
    const ends: [[number, number], number][] = [[segment.from, segment.fromAlpha], [segment.to, segment.toAlpha]];
    ends.forEach(([[u, v], alpha], end) => {
      positions.set(onPlane(plane, u, v), i * 6 + end * 3);
      colours.set([colour.r, colour.g, colour.b, alpha], i * 8 + end * 4);
    });
  });

  const geometry = new BufferGeometry();
  geometry.setAttribute("position", new BufferAttribute(positions, 3));
  geometry.setAttribute("color", new BufferAttribute(colours, 4));

  const lines = new LineSegments(geometry, new LineBasicMaterial({ vertexColors: true, transparent: true, depthWrite: false }));
  lines.renderOrder = -1;
  // it is as big as the scene, and always wanted
  lines.frustumCulled = false;
  lines.name = `grid-${kind}-${choice}`;
  return lines;
}

/**
 * @param plane the plane
 * @param u the coordinate along its first axis
 * @param v the coordinate along its second
 * @returns the point in the scene, with zero along the axis the plane does not span
 */
export function onPlane(plane: GridPlane, u: number, v: number): [number, number, number] {
  switch (plane) {
    case "xy":
      return [u, v, 0];
    case "xz":
      return [u, 0, v];
    default:
      return [0, u, v];
  }
}
