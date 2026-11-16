import type {Vector3} from "three";
import type {Vec3} from "../../api/types/structure";

/**
 * @param point a point
 * @returns it as a plain value
 */
export function toVec3(point: Vector3): Vec3 {
  return { x: point.x, y: point.y, z: point.z };
}

/**
 * @param a a point
 * @param b another
 * @returns the vector from `b` to `a`
 */
export function minus(a: Vector3, b: Vector3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
