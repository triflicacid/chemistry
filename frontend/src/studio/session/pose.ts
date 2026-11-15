import {Euler, MathUtils, Quaternion} from "three";
import type {Vec3} from "../../api/types/structure";
import type {Pose} from "./messages";

/** Where a molecule sits before anything has moved it: at the scene's origin, not turned. */
export const IDENTITY_POSE: Pose = { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 } };

/** The order the Inspector's angles are applied in. */
const ORDER = "XYZ";

/**
 * @param pose a pose
 * @returns its rotation as angles about x, y and z in degrees, for showing and editing
 */
export function eulerOf(pose: Pose): Vec3 {
  const { x, y, z, w } = pose.rotation;
  const angles = new Euler().setFromQuaternion(new Quaternion(x, y, z, w), ORDER);
  // adding zero turns a negative zero into a plain one, which would otherwise show as -0
  return { x: MathUtils.radToDeg(angles.x), y: MathUtils.radToDeg(angles.y), z: MathUtils.radToDeg(angles.z) };
}

/**
 * @param pose a pose
 * @param angles the rotation to give it, as angles about x, y and z in degrees
 * @returns the pose with that rotation, and the same position
 */
export function withEuler(pose: Pose, angles: Vec3): Pose {
  const turn = new Quaternion().setFromEuler(new Euler(MathUtils.degToRad(angles.x), MathUtils.degToRad(angles.y), MathUtils.degToRad(angles.z), ORDER));
  return { position: pose.position, rotation: { x: turn.x, y: turn.y, z: turn.z, w: turn.w } };
}

/**
 * @param first a pose
 * @param second another
 * @returns whether they put the molecule in the same place
 */
export function samePose(first: Pose, second: Pose): boolean {
  const a = first.position;
  const b = second.position;
  const p = first.rotation;
  const q = second.rotation;
  return a.x === b.x && a.y === b.y && a.z === b.z && p.x === q.x && p.y === q.y && p.z === q.z && p.w === q.w;
}
