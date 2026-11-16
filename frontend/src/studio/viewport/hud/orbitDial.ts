import {CylinderGeometry, Group, Mesh, MeshBasicMaterial, type PerspectiveCamera, SphereGeometry, TorusGeometry} from "three";

import {ARROW_AT, ARROW_REACH, SPHERE_RADIUS} from "./constants";
import {HudDial, type HudHandle, type Look, RESTING} from "./dial";

/** The axes' colours, as the triad and the scene's own axes draw them. */
const AXIS_COLOURS = [0xe41a1c, 0x2ca02c, 0x1f4fd8];

/**
 * The scene's axes as a ball: it has a ring about each axis, and it turns as the camera does, so it shows how the view is
 * turned and follows it as it is turned. Its arrowheads hold a turn to yaw or to pitch.
 */
export class OrbitDial extends HudDial {
  /** What turns as the camera does. */
  private readonly globe = new Group();
  private readonly fill = this.material();
  private readonly rings = AXIS_COLOURS.map((colour) => this.material(colour));
  private readonly lines = AXIS_COLOURS.map((colour) => this.material(colour));
  private readonly yawArrows = this.material();
  private readonly pitchArrows = this.material();

  constructor() {
    super();
    const fill = new Mesh(this.owned(new SphereGeometry(SPHERE_RADIUS, 24, 16)), this.fill);
    fill.renderOrder = 0;
    this.fill.depthWrite = false;
    this.globe.add(fill);

    // each ring is about an axis, so the green one, about the scene's up, is the path a yaw takes
    const ring = this.owned(new TorusGeometry(SPHERE_RADIUS, 0.9, 6, 64));
    const spoke = this.owned(new CylinderGeometry(0.7, 0.7, SPHERE_RADIUS * 2, 6));
    const [x, y, z] = [0, 1, 2] as const;
    const rings: [number, (mesh: Mesh) => void][] = [
      [x, (mesh) => (mesh.rotation.y = Math.PI / 2)],
      [y, (mesh) => (mesh.rotation.x = Math.PI / 2)],
      [z, () => undefined],
    ];
    for (const [axis, turn] of rings) {
      const mesh = new Mesh(ring, this.rings[axis] as MeshBasicMaterial);
      turn(mesh);
      mesh.renderOrder = 1;
      this.globe.add(mesh);
    }
    // a line along each axis; a cylinder stands along y until it is turned
    const lines: [number, (mesh: Mesh) => void][] = [
      [x, (mesh) => (mesh.rotation.z = Math.PI / 2)],
      [y, () => undefined],
      [z, (mesh) => (mesh.rotation.x = Math.PI / 2)],
    ];
    for (const [axis, turn] of lines) {
      const mesh = new Mesh(spoke, this.lines[axis] as MeshBasicMaterial);
      turn(mesh);
      mesh.renderOrder = 2;
      this.globe.add(mesh);
    }
    // the rings and lines are drawn in depth, so the near half of a ring is over the far half
    [...this.rings, ...this.lines].forEach((material) => (material.depthTest = true));
    this.scene.add(this.globe);

    // a pair of arrowheads each side of the sphere, across for a yaw and up and down for a pitch, which stay as they are
    this.arrowheads(this.yawArrows, [0, Math.PI]);
    this.arrowheads(this.pitchArrows, [Math.PI / 2, -Math.PI / 2]);
  }

  /**
   * What a press is on, if it is on the sphere.
   *
   * @param dx how far the press is right of the sphere's centre, in pixels
   * @param dy how far it is above the sphere's centre, in pixels
   * @returns the handle, or null if the press is not on the sphere or its arrowheads
   */
  hit(dx: number, dy: number): HudHandle | null {
    if (Math.hypot(Math.abs(dx) - ARROW_AT, dy) <= ARROW_REACH) {
      return "yaw";
    }
    if (Math.hypot(dx, Math.abs(dy) - ARROW_AT) <= ARROW_REACH) {
      return "pitch";
    }
    return Math.hypot(dx, dy) <= SPHERE_RADIUS + 3 ? "orbit" : null;
  }

  override update(camera: PerspectiveCamera): void {
    this.globe.quaternion.copy(camera.quaternion).invert();
  }

  paint(look: (handle: HudHandle) => Look): void {
    const set = (material: MeshBasicMaterial, handle: HudHandle) => {
      const { colour, opacity } = look(handle);
      material.color.setHex(colour);
      material.opacity = opacity;
    };
    set(this.yawArrows, "yaw");
    set(this.pitchArrows, "pitch");

    // the sphere keeps the axes' colours, and only gains strength and a tint when it is the one in use
    const orbit = look("orbit");
    this.fill.color.setHex(orbit === RESTING ? 0x808080 : 0x0969da);
    this.fill.opacity = orbit === RESTING ? 0.1 : 0.22;
    for (const material of [...this.rings, ...this.lines]) {
      material.opacity = orbit === RESTING ? 0.6 : 0.95;
    }
  }
}
