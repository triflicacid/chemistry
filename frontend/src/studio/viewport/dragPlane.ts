import { type Ray, Vector3 } from "three";

/** What a dragged atom is held to: anywhere in the view plane, along one axis, or within one plane. */
export type Constraint = "free" | "x" | "y" | "z" | "xy" | "yz" | "xz";

/** How close to parallel a ray may be to what it is meant to meet before the meeting is too far off to trust. */
const PARALLEL = 1e-6;

/** The direction of each axis, which a constraint named for one picks. */
const AXIS: Record<"x" | "y" | "z", Vector3> = {
  x: new Vector3(1, 0, 0),
  y: new Vector3(0, 1, 0),
  z: new Vector3(0, 0, 1),
};

/** The normal of the plane each plane constraint holds an atom in: the axis it leaves out. */
const NORMAL: Record<"xy" | "yz" | "xz", Vector3> = {
  xy: AXIS.z,
  yz: AXIS.x,
  xz: AXIS.y,
};

/**
 * Finds where a pointer ray meets the thing an atom is constrained to.
 *
 * @param ray the pointer's ray into the scene
 * @param anchor a point the constraint passes through, which is where the atom is when the constraint starts
 * @param constraint what the atom is held to
 * @param viewDirection the way the camera looks, which free movement is perpendicular to
 * @returns the point, or null if the ray never meets it: it runs parallel, or the plane is behind the pointer
 */
export function pointOnConstraint(ray: Ray, anchor: Vector3, constraint: Constraint, viewDirection: Vector3): Vector3 | null {
  if (constraint === "x" || constraint === "y" || constraint === "z") {
    return nearestOnAxis(ray, anchor, AXIS[constraint]);
  }

  const normal = constraint === "free" ? viewDirection : NORMAL[constraint];
  const along = ray.direction.dot(normal);
  if (Math.abs(along) < PARALLEL) {
    return null;
  }

  const distance = anchor.clone().sub(ray.origin).dot(normal) / along;
  return distance < 0 ? null : ray.at(distance, new Vector3());
}

/**
 * Finds how far an atom has moved from where a drag started, given where the pointer was then and is now.
 *
 * The atom keeps its offset from the pointer, so grabbing it away from its centre does not make it jump.
 *
 * @param before where the ray met the constraint when the drag started, from {@link pointOnConstraint}
 * @param now where the ray meets it now
 * @param start where the atom was when the drag started
 * @returns where the atom now is
 */
export function dragged(before: Vector3, now: Vector3, start: Vector3): Vector3 {
  return start.clone().add(now).sub(before);
}

/**
 * Picks the constraint a key press asks for. A letter holds the atom to that axis, and shift holds it to the plane the axis is perpendicular to.
 *
 * @param current the constraint now in force
 * @param key the letter pressed, in either case
 * @param shift whether shift was held
 * @returns the constraint to use, or null if the key asks for none
 */
export function constraintForKey(current: Constraint, key: string, shift: boolean): Constraint | null {
  const axis = key.toLowerCase();
  if (axis !== "x" && axis !== "y" && axis !== "z") {
    return null;
  }

  const asked: Constraint = shift ? (axis === "x" ? "yz" : axis === "y" ? "xz" : "xy") : axis;
  return asked === current ? "free" : asked;
}

function nearestOnAxis(ray: Ray, anchor: Vector3, axis: Vector3): Vector3 | null {
  // two lines, the axis through the anchor and the ray, and the parameter along the axis where they come closest
  const between = anchor.clone().sub(ray.origin);
  const aligned = axis.dot(ray.direction);
  const denominator = 1 - aligned * aligned;
  if (denominator < PARALLEL) {
    return null;
  }

  const onAxis = (aligned * ray.direction.dot(between) - axis.dot(between)) / denominator;
  return anchor.clone().addScaledVector(axis, onAxis);
}
