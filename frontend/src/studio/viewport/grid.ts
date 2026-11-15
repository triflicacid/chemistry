import {BufferAttribute, BufferGeometry, Color, LineBasicMaterial, LineSegments} from "three";

/** A square grid of lines in a plane, or nested spheres, which are what a polar grid is in three dimensions. */
export type GridKind = "cartesian" | "polar";

/** The plane a square grid lies in, named by the two scene axes that span it. */
export type GridPlane = "xy" | "xz" | "yz";

/** A plane, or all three at once. A polar grid fills space, so has no plane. */
export type GridPlaneChoice = GridPlane | "all";

/** How far apart the lines of a square grid, and the spheres of a polar one, are, in angstroms. */
export const GRID_SPACING = 1;

/** How far a square grid in one plane reaches from its centre in each direction, in angstroms, past which nothing is drawn. */
export const GRID_EXTENT = 30;

/**
 * How far each square grid reaches when all three planes are drawn. It is less than {@link GRID_EXTENT} because three
 * sheets crossing through the molecule are far busier than one, and a molecule is a few angstroms across.
 */
export const GRID_ALL_EXTENT = 10;

/**
 * How far a polar grid reaches, which is the radius of its outermost sphere. It is far less than a square grid's
 * because each sphere is a whole wireframe, and a few of them nested are as much as can be read.
 */
export const GRID_SPHERE_EXTENT = 15;

/** Every how many lines or spheres a stronger one is drawn, to give a sense of scale. */
export const GRID_MAJOR_EVERY = 5;

/** The angle between lines of latitude, and between lines of longitude, on the spheres of a polar grid, in degrees. */
export const GRID_ANGLE_DEGREES = 30;

/** The planes drawn when all of them are, in the order they are drawn. */
export const ALL_PLANES: readonly GridPlane[] = ["xz", "xy", "yz"];

/** How far apart the vertices along a straight line are, in angstroms. A line is cut into pieces so that it can fade along its length. */
const PIECE = 2;

/** How many chords make one circle of latitude. */
const CHORDS = 72;

/** How many chords make one meridian, from pole to pole. */
const MERIDIAN_CHORDS = 36;

/** The colour of each scene axis, as the axes drawn at the anchor have it. */
const AXIS_COLOUR = { x: 0xe41a1c, y: 0x2ca02c, z: 0x1f4fd8 } as const;

const GREY = 0x808080;

/** How opaque a line is at its strongest, by what it is. */
const STRENGTH = { minor: 0.22, major: 0.5, axis: 0.9 } as const;

type Axis = keyof typeof AXIS_COLOUR;

/** A point in the scene, relative to the centre of the grid. */
export type Point = [number, number, number];

/** One short straight piece of a grid. */
export interface GridSegment {
  from: Point;
  to: Point;
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
 * @param kind the kind of grid
 * @param choice the plane of a square grid, or all three, which a polar grid ignores
 * @returns how far the grid reaches from its centre, in angstroms
 */
export function extentOf(kind: GridKind, choice: GridPlaneChoice): number {
  if (kind === "polar") {
    return GRID_SPHERE_EXTENT;
  }
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
 * @param plane the plane
 * @param u the coordinate along its first axis
 * @param v the coordinate along its second
 * @returns the point in the scene, with zero along the axis the plane does not span
 */
export function onPlane(plane: GridPlane, u: number, v: number): Point {
  switch (plane) {
    case "xy":
      return [u, v, 0];
    case "xz":
      return [u, 0, v];
    default:
      return [0, u, v];
  }
}

/**
 * Works out every piece of a grid, with no reference to drawing it.
 * <p>
 * A square grid lies in a plane, or in all three. It has a line every {@link GRID_SPACING}, a stronger one every
 * {@link GRID_MAJOR_EVERY}, and the two lines through the centre in the colours of the axes they run along.
 * <p>
 * A polar grid is spherical: nested spheres every {@link GRID_SPACING} out to {@link GRID_SPHERE_EXTENT}, stronger every
 * {@link GRID_MAJOR_EVERY}, each drawn as lines of latitude and longitude every {@link GRID_ANGLE_DEGREES}, with the poles
 * on the scene's y axis, which is up. The six half-axes from the centre are in the axes' colours. It has no plane, so
 * `choice` is ignored.
 *
 * @param kind square or polar
 * @param choice the plane of a square grid, or all three
 * @returns its pieces, which fade towards the edge
 */
export function gridSegments(kind: GridKind, choice: GridPlaneChoice): GridSegment[] {
  if (kind === "polar") {
    return spherical(GRID_SPHERE_EXTENT);
  }

  const extent = extentOf(kind, choice);
  const planes = choice === "all" ? ALL_PLANES : [choice];
  return planes.flatMap((plane) => square(plane, extent));
}

function square(plane: GridPlane, extent: number): GridSegment[] {
  const [first, second] = axesOf(plane);
  const segments: GridSegment[] = [];
  const lines = Math.round(extent / GRID_SPACING);

  for (let i = -lines; i <= lines; i++) {
    const at = i * GRID_SPACING;
    const strength = i === 0 ? STRENGTH.axis : i % GRID_MAJOR_EVERY === 0 ? STRENGTH.major : STRENGTH.minor;

    // the line at a first coordinate of zero runs along the second axis, and the reverse
    const alongSecond = i === 0 ? AXIS_COLOUR[second] : GREY;
    const alongFirst = i === 0 ? AXIS_COLOUR[first] : GREY;
    for (let along = -extent; along < extent; along += PIECE) {
      const end = Math.min(along + PIECE, extent);
      segments.push(piece(onPlane(plane, at, along), onPlane(plane, at, end), strength, alongSecond, extent));
      segments.push(piece(onPlane(plane, along, at), onPlane(plane, end, at), strength, alongFirst, extent));
    }
  }
  return segments;
}

function spherical(extent: number): GridSegment[] {
  const segments: GridSegment[] = [];
  const shells = Math.round(extent / GRID_SPACING);
  const step = (GRID_ANGLE_DEGREES * Math.PI) / 180;

  for (let shell = 1; shell <= shells; shell++) {
    const radius = shell * GRID_SPACING;
    const strength = shell % GRID_MAJOR_EVERY === 0 ? STRENGTH.major : STRENGTH.minor;

    // circles of latitude, each parallel to the equator, which is the one at zero
    for (let elevation = -Math.PI / 2 + step; elevation < Math.PI / 2 - 1e-9; elevation += step) {
      const around = radius * Math.cos(elevation);
      const height = radius * Math.sin(elevation);
      for (let chord = 0; chord < CHORDS; chord++) {
        const a = (chord / CHORDS) * 2 * Math.PI;
        const b = ((chord + 1) / CHORDS) * 2 * Math.PI;
        segments.push(piece([around * Math.cos(a), height, around * Math.sin(a)], [around * Math.cos(b), height, around * Math.sin(b)], strength, GREY, extent));
      }
    }

    // lines of longitude, each a half circle from one pole to the other
    for (let longitude = 0; longitude < 2 * Math.PI - 1e-9; longitude += step) {
      for (let chord = 0; chord < MERIDIAN_CHORDS; chord++) {
        const from = meridian(radius, longitude, (chord / MERIDIAN_CHORDS) * Math.PI);
        const to = meridian(radius, longitude, ((chord + 1) / MERIDIAN_CHORDS) * Math.PI);
        segments.push(piece(from, to, strength, GREY, extent));
      }
    }
  }

  // the half-axes out from the centre, so that the sphere can be read against the scene's axes
  const directions: [Point, Axis][] = [
    [[1, 0, 0], "x"], [[-1, 0, 0], "x"],
    [[0, 1, 0], "y"], [[0, -1, 0], "y"],
    [[0, 0, 1], "z"], [[0, 0, -1], "z"],
  ];
  for (const [[x, y, z], axis] of directions) {
    for (let along = 0; along < extent; along += PIECE) {
      const end = Math.min(along + PIECE, extent);
      segments.push(piece([x * along, y * along, z * along], [x * end, y * end, z * end], STRENGTH.axis, AXIS_COLOUR[axis], extent));
    }
  }
  return segments;
}

/**
 * @param radius the radius of the sphere
 * @param longitude how far round from the +x direction, in radians
 * @param polar how far down from the +y pole, in radians
 * @returns the point on the sphere
 */
function meridian(radius: number, longitude: number, polar: number): Point {
  return [radius * Math.sin(polar) * Math.cos(longitude), radius * Math.cos(polar), radius * Math.sin(polar) * Math.sin(longitude)];
}

/** A piece of a line of a given strength, faded by how far each end is from the centre. */
function piece(from: Point, to: Point, strength: number, colour: number, extent: number): GridSegment {
  return {
    from,
    to,
    fromAlpha: strength * fade(Math.hypot(...from), extent),
    toAlpha: strength * fade(Math.hypot(...to), extent),
    colour,
  };
}

/**
 * Draws a grid about the origin: a square one in one of the scene's planes or in all three, or a polar one of nested
 * spheres.
 * <p>
 * It is transparent and writes no depth, so atoms in front of it hide it and it never hides an atom, and it is drawn
 * first. It is not pickable: nothing registers it as a part. With all three planes the lines where two of them meet are
 * drawn once by each, which at their opacity is not noticeable.
 *
 * @param kind square or polar
 * @param choice the plane of a square grid, or all three; a polar grid ignores it
 * @returns the lines, which the caller adds to the scene and, when done with them, disposes the geometry and material of
 */
export function buildGrid(kind: GridKind, choice: GridPlaneChoice): LineSegments {
  const segments = gridSegments(kind, choice);
  const positions = new Float32Array(segments.length * 6);
  const colours = new Float32Array(segments.length * 8);

  segments.forEach((segment, i) => {
    const colour = new Color(segment.colour);
    positions.set(segment.from, i * 6);
    positions.set(segment.to, i * 6 + 3);
    colours.set([colour.r, colour.g, colour.b, segment.fromAlpha], i * 8);
    colours.set([colour.r, colour.g, colour.b, segment.toAlpha], i * 8 + 4);
  });

  const geometry = new BufferGeometry();
  geometry.setAttribute("position", new BufferAttribute(positions, 3));
  geometry.setAttribute("color", new BufferAttribute(colours, 4));

  const lines = new LineSegments(geometry, new LineBasicMaterial({ vertexColors: true, transparent: true, depthWrite: false }));
  lines.renderOrder = -1;
  // it is as big as the scene, and always wanted
  lines.frustumCulled = false;
  lines.name = kind === "polar" ? "grid-polar" : `grid-${kind}-${choice}`;
  return lines;
}
