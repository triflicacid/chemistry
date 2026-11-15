import type {Vector3} from "three";
import {UP} from "./primitives";

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
 * How far a camera is rolled: the angle from the up it would have if it were level, which is the scene's up as near as it
 * can be while still being across the way the camera looks, round to the camera's own up. It is the angle
 * {@link rolled} turns a level camera's up by.
 *
 * @param up which way is up on the screen now, in the scene, as a unit vector
 * @param forward the direction the camera looks in, not necessarily of unit length
 * @returns the roll, from -π to π, with the picture turned counterclockwise for a positive roll; 0 for a camera looking
 *     along the scene's up, which has no level
 */
export function rollOf(up: Vector3, forward: Vector3): number {
  const along = forward.clone().normalize();
  const level = UP.clone().addScaledVector(along, -UP.dot(along));
  if (level.lengthSq() < 1e-12) {
    return 0;
  }
  level.normalize();
  return Math.atan2(along.dot(level.clone().cross(up)), level.dot(up));
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
