import type {Vec3} from "../../api/types/structure";
import {Observable} from "./observable";

/** The furthest the camera may look up or down, in degrees, short of straight along the scene's up, which has no yaw. */
export const MAX_PITCH = 89;

/** The nearest the camera may be to the point it looks at, in ångströms. */
export const MIN_DISTANCE = 0.1;

/** The narrowest and widest the camera's vertical field of view may be, in degrees. */
export const MIN_FOV = 5;
export const MAX_FOV = 120;

/**
 * Where the camera is and how it looks. It orbits a point: its position is that point plus a distance along a direction
 * given by the yaw and pitch.
 */
export interface CameraState {
  /** Where the camera is, in the scene, in ångströms. */
  position: Vec3;
  /** The point the camera looks at and orbits, in the scene, in ångströms. */
  target: Vec3;
  /** How far round the scene's up the camera is from the +z axis, towards +x, in degrees. */
  yaw: number;
  /** How far above the level of the point looked at the camera is, in degrees. */
  pitch: number;
  /** How far the camera is from the point it looks at, in ångströms. */
  distance: number;
  /** The camera's vertical field of view, in degrees. */
  fov: number;
  /**
   * How far the picture is turned in the screen's plane about the way the camera looks, from level, in degrees: the
   * angle from level up to the camera's up, so that a positive roll turns what is seen counterclockwise.
   */
  roll: number;
}

/**
 * A change to the camera: any of these at once. Where it can be read two ways, a position wins over a yaw, pitch or
 * distance, and a target given alone leaves the camera where it is, so it turns to look at it.
 */
export type CameraEdit = Partial<Pick<CameraState, "position" | "target" | "yaw" | "pitch" | "distance" | "fov" | "roll">>;

/**
 * Works out the yaw, pitch and distance of a camera from where it is and what it looks at.
 *
 * @param position where the camera is
 * @param target what it looks at
 * @returns the three, with yaw and pitch in degrees; a camera on its target has no direction, so yaw and pitch are 0
 */
export function orbitOf(position: Vec3, target: Vec3): Pick<CameraState, "yaw" | "pitch" | "distance"> {
  const x = position.x - target.x;
  const y = position.y - target.y;
  const z = position.z - target.z;
  const distance = Math.hypot(x, y, z);
  if (distance === 0) {
    return { yaw: 0, pitch: 0, distance };
  }
  return {
    yaw: (Math.atan2(x, z) * 180) / Math.PI,
    pitch: (Math.asin(Math.max(-1, Math.min(1, y / distance))) * 180) / Math.PI,
    distance,
  };
}

/**
 * Puts a camera at a yaw, pitch and distance from what it looks at. It is the inverse of {@link orbitOf}.
 *
 * @param target what the camera looks at
 * @param yaw how far round the scene's up, in degrees
 * @param pitch how far above the level of the target, in degrees
 * @param distance how far from the target
 * @returns where the camera goes
 */
export function positionAt(target: Vec3, yaw: number, pitch: number, distance: number): Vec3 {
  const around = (yaw * Math.PI) / 180;
  const above = (pitch * Math.PI) / 180;
  return {
    x: target.x + Math.cos(above) * Math.sin(around) * distance,
    y: target.y + Math.sin(above) * distance,
    z: target.z + Math.cos(above) * Math.cos(around) * distance,
  };
}

/**
 * Applies an edit to a camera.
 *
 * @param camera how the camera is
 * @param edit what to change
 * @returns the position, target, field of view and roll to give the camera, with the roll kept as it was unless the edit
 *     gives one and brought to between -180 and 180; or null if the edit would put the camera on its target or is not made
 *     of finite numbers
 */
export function edited(camera: CameraState, edit: CameraEdit): Pick<CameraState, "position" | "target" | "fov" | "roll"> | null {
  const target = edit.target ?? camera.target;
  const fov = Math.max(MIN_FOV, Math.min(MAX_FOV, edit.fov ?? camera.fov));
  const position =
    edit.position ??
    (edit.yaw !== undefined || edit.pitch !== undefined || edit.distance !== undefined
      ? positionAt(
          target,
          edit.yaw ?? camera.yaw,
          Math.max(-MAX_PITCH, Math.min(MAX_PITCH, edit.pitch ?? camera.pitch)),
          edit.distance ?? camera.distance,
        )
      : camera.position);

  const turned = edit.roll ?? camera.roll;
  const roll = turned - 360 * Math.round(turned / 360);

  const numbers = [fov, roll, target.x, target.y, target.z, position.x, position.y, position.z];
  if (!numbers.every(Number.isFinite) || orbitOf(position, target).distance < MIN_DISTANCE) {
    return null;
  }
  return { position, target, fov, roll };
}

/**
 * The camera, as the Inspector shows it. The viewport tells it where the camera is, and the Inspector asks it to change.
 */
export class CameraModel extends Observable<CameraState | null> {
  private handler: ((edit: CameraEdit) => void) | null = null;

  constructor() {
    super(null);
  }

  /**
   * Says where the camera is now.
   *
   * @param camera how the camera is
   */
  report(camera: CameraState): void {
    this.set(camera);
  }

  /**
   * Takes on the job of moving the camera when asked to.
   *
   * @param handler moves the camera
   * @returns how to stop
   */
  handle(handler: (edit: CameraEdit) => void): () => void {
    this.handler = handler;
    return () => {
      if (this.handler === handler) {
        this.handler = null;
      }
    };
  }

  /**
   * Asks for the camera to be changed. Nothing happens if no viewport is there to do it.
   *
   * @param edit what to change
   */
  edit(edit: CameraEdit): void {
    this.handler?.(edit);
  }
}
