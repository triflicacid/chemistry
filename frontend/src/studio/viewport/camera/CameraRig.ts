import {Box3, type Quaternion, Vector3} from "three";
import type {Vec3} from "../../../api/types/structure";
import {FIELD_OF_VIEW, type Stage} from "../../../render/stage";
import {type CameraEdit, type CameraState, edited, orbitOf} from "../../models/CameraModel";
import type {MoleculeScene} from "../scene/MoleculeScene";

/** How far, in radians, the camera may have turned since a triad click and still count as not having moved. */
export const SAME_VIEW = 1e-3;

/** How far, in degrees, the view turns for each pixel a sphere is dragged. */
const ORBIT_DEGREES_PER_PIXEL = 0.4;

function toVec3(point: Vector3): Vec3 {
  return { x: point.x, y: point.y, z: point.z };
}

export class CameraRig {
  /** The point the view was last anchored on, in the molecule's own frame. */
  anchor = new Vector3();
  /** The last view a triad click snapped to. */
  snapped: { axis: Vector3; sign: 1 | -1; orientation: Quaternion } | null = null;

  /** How many times the view is placing the camera itself, which is not the user moving it. */
  private placing = 0;

  /**
   * @param stage the stage whose camera this moves
   * @param scene the molecule the camera frames
   * @param anchored told when the anchor has moved, so what sits on it can follow
   */
  constructor(
    private readonly stage: Stage,
    private readonly scene: MoleculeScene,
    private readonly anchored: () => void,
  ) {}

  /** Whether a camera change now is the user's, rather than the view placing the camera itself. */
  get byUser(): boolean {
    return this.placing === 0;
  }

  /**
   * Runs something that moves the camera as the view's own doing rather than the user's, so that it is not reported as theirs.
   *
   * @param place what to run
   */
  placeWhile(place: () => void): void {
    this.placing++;
    try {
      place();
    } finally {
      this.placing--;
    }
  }

  /**
   * Says where the camera is.
   *
   * @returns its position, what it looks at, how it is turned about that, and its field of view
   */
  cameraState(): CameraState {
    const { camera, controls } = this.stage;
    const position = toVec3(camera.position);
    const target = toVec3(controls.target);
    return { position, target, ...orbitOf(position, target), fov: camera.fov, roll: (this.stage.rolledBy * 180) / Math.PI };
  }

  /**
   * Moves the camera.
   *
   * @param edit what to change; one that would put the camera on what it looks at is ignored
   */
  setCamera(edit: CameraEdit): void {
    const next = edited(this.cameraState(), edit);
    if (next) {
      this.stage.place(new Vector3(next.position.x, next.position.y, next.position.z), new Vector3(next.target.x, next.target.y, next.target.z), next.fov, (next.roll * Math.PI) / 180);
    }
  }

  /** Brings the middle of the molecule to the middle of the view. */
  centreCamera(): void {
    const middle = this.scene.middleOfAtoms();
    this.stage.pivotOn(middle);
    this.anchorOn(middle);
  }

  /**
   * @param dx how far the pointer went across, in pixels
   * @param dy how far it went down, in pixels
   */
  orbitBy(dx: number, dy: number): void {
    const now = this.cameraState();
    this.setCamera({ yaw: now.yaw - dx * ORBIT_DEGREES_PER_PIXEL, pitch: now.pitch + dy * ORBIT_DEGREES_PER_PIXEL });
  }

  /**
   * @param dx how far the pointer went across, in pixels
   * @param dy how far it went down, in pixels
   */
  panBy(dx: number, dy: number): void {
    const { camera, controls, canvas } = this.stage;
    const height = Math.max(1, canvas.clientHeight);
    const perPixel = (2 * camera.position.distanceTo(controls.target) * Math.tan((camera.fov * Math.PI) / 360)) / height;
    const across = new Vector3(1, 0, 0).applyQuaternion(camera.quaternion);
    const up = new Vector3(0, 1, 0).applyQuaternion(camera.quaternion);
    const to = controls.target.clone().addScaledVector(across, -dx * perPixel).addScaledVector(up, dy * perPixel);
    this.stage.pivotOn(to);
  }

  /**
   * Flies the camera: moves it, and what it looks at with it, along its own axes, so the view keeps its angle.
   *
   * @param right how far to the right, in scene units
   * @param up how far up the screen
   * @param forward how far the way the camera looks
   */
  moveBy(right: number, up: number, forward: number): void {
    const { camera, controls } = this.stage;
    const to = controls.target.clone();
    to.addScaledVector(new Vector3(1, 0, 0).applyQuaternion(camera.quaternion), right);
    to.addScaledVector(new Vector3(0, 1, 0).applyQuaternion(camera.quaternion), up);
    to.addScaledVector(new Vector3(0, 0, -1).applyQuaternion(camera.quaternion), forward);
    this.stage.pivotOn(to);
  }

  /** Puts the camera at the defaults. */
  resetCamera(): void {
    // framing works out how far back to stand from the field of view, so that comes first
    this.setCamera({ fov: FIELD_OF_VIEW });
    this.frameScene();
  }

  /** Points the camera at the whole scene. */
  frameScene(): void {
    this.frameAtoms(this.scene.atoms.map((node) => node.atom.id));
  }

  /**
   * Points the camera at some atoms, orbiting their centre.
   *
   * @param ids the atoms to show; if none of them are drawn, the whole scene is framed
   */
  frameAtoms(ids: readonly string[]): void {
    const nodes = ids.flatMap((id) => this.scene.atomById.get(id) ?? []);
    if (nodes.length === 0 && this.scene.atoms.length > 0) {
      this.frameScene();
      return;
    }
    if (nodes.length === 0) {
      this.stage.frame(new Vector3(), 3);
      this.anchorOn(new Vector3());
      return;
    }

    const box = new Box3();
    nodes.forEach((node) => box.expandByPoint(this.scene.worldOf(node)));
    const centre = box.getCenter(new Vector3());
    let radius = 1;
    for (const node of nodes) {
      radius = Math.max(radius, this.scene.worldOf(node).distanceTo(centre) + Math.max(this.scene.radiusOf(node), 0.3));
    }
    this.stage.frame(centre, radius + 0.5);
    this.anchorOn(centre);
  }

  /**
   * Anchors the view on an atom.
   *
   * @param id the atom's identifier; one that is not drawn is ignored
   */
  anchorOnAtom(id: string): void {
    const node = this.scene.atomById.get(id);
    if (node) {
      const world = this.scene.worldOf(node);
      this.stage.pivotOn(world);
      this.anchorOn(world);
    }
  }

  /**
   * Records where the view has been anchored, which is where the molecule's handles sit when its pivot is the anchor.
   *
   * @param world the point the view now orbits, in the scene
   */
  anchorOn(world: Vector3): void {
    this.anchor = this.scene.content.worldToLocal(world.clone());
    this.anchored();
  }
}
