import {Color, Group, Vector3} from "three";
import type {Connection3D} from "../api/types/structure";
import type {Primitives} from "./primitives";

/** How atoms and bonds are drawn. */
export type Style = "ball_and_stick" | "sticks" | "wireframe" | "spacefill";
export const STYLES: readonly Style[] = ["ball_and_stick", "sticks", "wireframe", "spacefill"];

/**
 * @param value anything
 * @returns it if it is a style, and otherwise the default
 */
export function asStyle(value: unknown): Style {
  return STYLES.find((style) => style === value) ?? "ball_and_stick";
}

/** What is needed of a bond to draw it: the two atoms it joins, by index, and its order. */
export type BondEnds = Pick<Connection3D, "first" | "second" | "order">;

const BALL_FRACTION = 0.25;
const LONE_ATOM_RADIUS = 0.2;

/** The radius of a ball drawn for an atom that is not bonded to anything shown, in `sticks` and `wireframe`. */
export { LONE_ATOM_RADIUS };

/**
 * @param radius the atom's van der Waals radius
 * @param style how the molecule is drawn
 * @param bonded whether any bond shown touches the atom
 * @returns the radius, which is zero where no ball is drawn
 */
export function ballRadius(radius: number, style: Style, bonded: boolean): number {
  switch (style) {
    case "spacefill":
      return radius;
    case "ball_and_stick":
      return BALL_FRACTION * radius;
    default:
      return bonded ? 0 : LONE_ATOM_RADIUS;
  }
}

/**
 * Builds the drawing of one bond.
 *
 * @param kit the shared geometry to build from
 * @param link the connection to draw
 * @param sites the atoms or sites, by index, which give the colour at each end
 * @param style how the molecule is drawn, which sets the thickness
 * @param point where a site is, by site index
 * @param neighbours the sites each drawn site is connected to, which say which way a multiple bond fans out
 * @param ringCentre the centre of the ring system the bond is in, if it is aromatic
 * @returns the bond's meshes, or null if its ends coincide so that there is nothing to draw
 */
export function buildBond(
  kit: Primitives,
  link: BondEnds,
  sites: ReadonlyArray<{ colour: string }>,
  style: Style,
  point: (index: number) => Vector3,
  neighbours: Map<number, number[]>,
  ringCentre: Vector3 | undefined,
): Group | null {
  const from = point(link.first);
  const to = point(link.second);
  const along = to.clone().sub(from);
  if (along.lengthSq() < 1e-12) {
    return null;
  }
  along.normalize();

  const stick = style === "sticks";
  const thin = style === "wireframe";
  const width = stick ? 0.15 : thin ? 0.02 : 0.07;
  const spacing = stick ? 0.2 : thin ? 0.08 : 0.14;

  const toward = across(link, from, to, along, point, neighbours, ringCentre);
  const group = new Group();
  const lines: { offset: number; dashed: boolean; width: number }[] = (() => {
    switch (link.order) {
      case "DOUBLE":
        return [-0.5, 0.5].map((side) => ({ offset: side * spacing, dashed: false, width: width * 0.7 }));
      case "TRIPLE":
        return [-1, 0, 1].map((side) => ({ offset: side * spacing, dashed: false, width: width * 0.6 }));
      case "QUADRUPLE":
        return [-1.5, -0.5, 0.5, 1.5].map((side) => ({ offset: side * spacing, dashed: false, width: width * 0.5 }));
      case "AROMATIC":
        return [
          { offset: 0, dashed: false, width },
          { offset: spacing * 1.4, dashed: true, width: width * 0.7 },
        ];
      default:
        return [{ offset: 0, dashed: false, width }];
    }
  })();

  const first = new Color(sites[link.first]?.colour ?? "#888888");
  const second = new Color(sites[link.second]?.colour ?? "#888888");
  for (const line of lines) {
    const shift = toward.clone().multiplyScalar(line.offset);
    const a = from.clone().add(shift);
    const b = to.clone().add(shift);
    if (line.dashed) {
      const inset = a.distanceTo(b) * 0.15;
      a.addScaledVector(along, inset);
      b.addScaledVector(along, -inset);
      addDashes(kit, group, a, b, line.width, first, second);
    } else {
      const middle = a.clone().add(b).multiplyScalar(0.5);
      group.add(kit.cylinder(a, middle, line.width, kit.solid(first)));
      group.add(kit.cylinder(middle, b, line.width, kit.solid(second)));
    }
  }
  return group;
}

/**
 * Works out which way a bond's extra lines are laid out: towards the ring, or into the plane of a neighbour.
 *
 * @returns a unit vector at right angles to the bond
 */
function across(
  link: BondEnds,
  from: Vector3,
  to: Vector3,
  along: Vector3,
  point: (index: number) => Vector3,
  neighbours: Map<number, number[]>,
  ringCentre: Vector3 | undefined,
): Vector3 {
  const candidates: Vector3[] = [];
  if (ringCentre) {
    candidates.push(ringCentre.clone().sub(from.clone().add(to).multiplyScalar(0.5)));
  }
  for (const [atom, other] of [[link.first, link.second], [link.second, link.first]] as const) {
    for (const next of neighbours.get(atom) ?? []) {
      if (next !== other) {
        candidates.push(point(next).sub(point(atom)));
      }
    }
  }
  for (const candidate of candidates) {
    const flat = candidate.addScaledVector(along, -candidate.dot(along));
    if (flat.lengthSq() > 1e-6) {
      return flat.normalize();
    }
  }
  const any = Math.abs(along.x) < 0.9 ? new Vector3(1, 0, 0) : new Vector3(0, 1, 0);
  return along.clone().cross(any).normalize();
}

/**
 * Adds a dashed line between two points, the first half in one colour and the second in another.
 *
 * @param kit the shared geometry to build from
 * @param group where the dashes are added
 * @param a where the line starts
 * @param b where it ends
 * @param width the radius of each dash
 * @param first the colour of the half nearer `a`
 * @param second the colour of the half nearer `b`
 */
function addDashes(kit: Primitives, group: Group, a: Vector3, b: Vector3, width: number, first: Color, second: Color): void {
  const length = a.distanceTo(b);
  const period = 0.2;
  const count = Math.max(1, Math.round(length / period));
  const step = length / count;
  const direction = b.clone().sub(a).normalize();
  for (let i = 0; i < count; i++) {
    const start = a.clone().addScaledVector(direction, i * step + step * 0.15);
    const end = a.clone().addScaledVector(direction, i * step + step * 0.85);
    const colour = i < count / 2 ? first : second;
    group.add(kit.cylinder(start, end, width, kit.solid(colour)));
  }
}
