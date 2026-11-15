import type {Vector3} from "three";

/**
 * Where a camera's up direction goes when the camera is rolled: turned about the direction it looks in.
 * A positive angle turns what is seen counterclockwise on the screen.
 *
 * @param up which way is up on the screen now, in the scene, as a unit vector; this is the camera's own up, which
 *     differs from the scene's when the camera is looking along it
 * @param forward the direction the camera looks in, not necessarily of unit length
 * @param angle how far to roll, in radians
 * @returns the new up direction, as a unit vector
 */
export function rolled(up: Vector3, forward: Vector3, angle: number): Vector3 {
  return up.clone().applyAxisAngle(forward.clone().normalize(), angle).normalize();
}

/**
 * The shortest way round from one angle to another, so that a pointer going past the point where angles wrap is a small
 * step and not nearly a whole turn.
 *
 * @param from the angle before, in radians
 * @param to the angle after, in radians
 * @returns the step from `from` to `to`, from -π up to π, counterclockwise positive
 */
export function sweep(from: number, to: number): number {
  const turn = 2 * Math.PI;
  return ((((to - from) % turn) + turn + Math.PI) % turn) - Math.PI;
}
