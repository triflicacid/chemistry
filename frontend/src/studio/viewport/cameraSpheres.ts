import {
  CircleGeometry,
  CylinderGeometry,
  Group,
  Mesh,
  MeshBasicMaterial,
  OrthographicCamera,
  type PerspectiveCamera,
  PlaneGeometry,
  RingGeometry,
  Scene,
  Shape,
  ShapeGeometry,
  SphereGeometry,
  TorusGeometry,
  Vector2,
  type WebGLRenderer,
} from "three";

/**
 * What a press on the camera spheres takes hold of: the orbit sphere's body, which turns the view any way, or its
 * arrowheads, which turn it only across (yaw) or only up and down (pitch); the pan sphere's ball, which moves the view any
 * way, or its arms, which move it only across or only up and down.
 */
export type SphereHandle = "orbit" | "yaw" | "pitch" | "pan" | "pan-x" | "pan-y";

/** A point or offset in a sphere's own pixels, from its centre, with x to the right and y up. */
export interface SpherePoint {
  x: number;
  y: number;
}

/** The radius of the orbit sphere, and of the track the pan ball moves in, in CSS pixels. */
export const SPHERE_RADIUS = 34;

/** How far from the centre the arrowheads are, which is also where the ends of the pan arms are, in CSS pixels. */
const ARROW_AT = SPHERE_RADIUS + 13;

/** How far from an arrowhead's middle a press may be and still take hold of it, in CSS pixels. */
const ARROW_REACH = 11;

/** The radius of the pan ball, in CSS pixels. */
export const BALL_RADIUS = 12;

/** How far either side of an arm's line a press may be and still take hold of it, in CSS pixels. */
const ARM_REACH = 6;

/** How far the pan ball can be from the centre, which keeps it inside its track. */
export const BALL_TRAVEL = SPHERE_RADIUS - BALL_RADIUS;

/** The axes' colours, as the triad and the scene's own axes draw them. */
const AXIS_COLOURS = [0xe41a1c, 0x2ca02c, 0x1f4fd8];

const RESTING = { colour: 0x808080, opacity: 0.35 };
const HOVERED = { colour: 0x0969da, opacity: 0.8 };
const ACTIVE = { colour: 0x0969da, opacity: 0.95 };

/**
 * What the orbit sphere is being pressed on, if the press is on it.
 *
 * @param dx how far the press is right of the sphere's centre, in pixels
 * @param dy how far it is above the sphere's centre, in pixels
 * @returns the handle, or null if the press is not on the orbit sphere or its arrowheads
 */
export function orbitHandleAt(dx: number, dy: number): SphereHandle | null {
  if (Math.hypot(Math.abs(dx) - ARROW_AT, dy) <= ARROW_REACH) {
    return "yaw";
  }
  if (Math.hypot(dx, Math.abs(dy) - ARROW_AT) <= ARROW_REACH) {
    return "pitch";
  }
  return Math.hypot(dx, dy) <= SPHERE_RADIUS + 3 ? "orbit" : null;
}

/**
 * What the pan sphere is being pressed on, if the press is on it. The ball comes first, then the arms, and anywhere else in
 * the track moves the view any way.
 *
 * @param dx how far the press is right of the sphere's centre, in pixels
 * @param dy how far it is above the sphere's centre, in pixels
 * @param ball where the ball is, from the centre
 * @returns the handle, or null if the press is not on the pan sphere or its arms
 */
export function panHandleAt(dx: number, dy: number, ball: SpherePoint = { x: 0, y: 0 }): SphereHandle | null {
  if (Math.hypot(dx - ball.x, dy - ball.y) <= BALL_RADIUS + 3) {
    return "pan";
  }
  const start = BALL_RADIUS + 8;
  const end = ARROW_AT + ARROW_REACH;
  if (Math.abs(dy) <= ARM_REACH && Math.abs(dx) >= start && Math.abs(dx) <= end) {
    return "pan-x";
  }
  if (Math.abs(dx) <= ARM_REACH && Math.abs(dy) >= start && Math.abs(dy) <= end) {
    return "pan-y";
  }
  return Math.hypot(dx, dy) <= SPHERE_RADIUS ? "pan" : null;
}

/**
 * Two spheres in the lower right corner of the canvas to move the camera with, drawn like the triad and the roll ring.
 *
 * The orbit sphere is the scene's axes as a ball: it has a ring about each axis, and it turns as the camera does, so it
 * shows how the view is turned and follows it as it is turned. Its arrowheads hold a turn to yaw or to pitch. The pan
 * sphere has a ball that is pulled out of the middle of its track as the view is panned, and let go of back to the
 * middle, and arms that hold a pan across or up and down.
 */
export class CameraSpheres {
  /** Whether to draw them, and take presses. */
  visible = false;

  /** What the pointer is over, which brightens it. */
  hovered: SphereHandle | null = null;

  /** What is being dragged, which brightens it further. */
  active: SphereHandle | null = null;

  /** Where the pan ball is, from its centre, in pixels with y up, which the view sets as it pans. */
  ball: SpherePoint = { x: 0, y: 0 };

  private readonly camera: OrthographicCamera;
  private readonly orbitScene = new Scene();
  private readonly panScene = new Scene();
  /** What turns as the camera does. */
  private readonly globe = new Group();
  private readonly ballMark = new Group();

  private readonly fill = this.material();
  private readonly rings = AXIS_COLOURS.map((colour) => this.material(colour));
  private readonly lines = AXIS_COLOURS.map((colour) => this.material(colour));
  private readonly yawArrows = this.material();
  private readonly pitchArrows = this.material();
  private readonly track = this.material();
  private readonly ballFill = this.material();
  private readonly ballShine = this.material(0xffffff);
  private readonly armsX = this.material();
  private readonly armsY = this.material();
  private readonly geometries: { dispose(): void }[] = [];

  /**
   * @param size the side of the square each sphere is drawn in, in CSS pixels, which must be at least twice the distance
   *     from a sphere's centre to its arrowheads plus their size
   * @param margin how far the pair is from the canvas's right and bottom edges, in CSS pixels
   */
  constructor(
    private readonly size: number,
    private readonly margin: number,
  ) {
    this.camera = new OrthographicCamera(-size / 2, size / 2, size / 2, -size / 2, -200, 200);
    this.buildOrbit();
    this.buildPan();
  }

  /**
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   * @returns what the pointer is over, or null if they are hidden or it is over neither
   */
  hit(canvas: DOMRect, x: number, y: number): SphereHandle | null {
    if (!this.visible) {
      return null;
    }
    const orbit = this.offsetFrom(canvas, x, y, this.orbitCentre(canvas.width));
    const pan = this.offsetFrom(canvas, x, y, this.panCentre(canvas.width));
    return orbitHandleAt(orbit.x, orbit.y) ?? panHandleAt(pan.x, pan.y, this.ball);
  }

  /** The distance between the two spheres' centres, which are in a row. */
  private get spacing(): number {
    return this.size + 8;
  }

  private orbitCentre(width: number): number {
    return width - this.margin - this.size / 2;
  }

  private panCentre(width: number): number {
    return this.orbitCentre(width) - this.spacing;
  }

  private offsetFrom(canvas: DOMRect, x: number, y: number, centreX: number): SpherePoint {
    return { x: x - canvas.left - centreX, y: canvas.bottom - this.margin - this.size / 2 - y };
  }

  /**
   * Draws them over what the renderer has already drawn.
   *
   * @param renderer the renderer, which has just drawn the scene
   * @param from the camera the scene was drawn with, whose orientation the orbit sphere takes
   */
  draw(renderer: WebGLRenderer, from: PerspectiveCamera): void {
    if (!this.visible) {
      return;
    }

    this.globe.quaternion.copy(from.quaternion).invert();
    this.ballMark.position.set(this.ball.x, this.ball.y, 0);
    this.paint();

    const whole = renderer.getSize(new Vector2());
    const clears = renderer.autoClear;
    renderer.autoClear = false;
    renderer.setScissorTest(true);
    this.render(renderer, this.orbitScene, this.orbitCentre(whole.x) - this.size / 2);
    this.render(renderer, this.panScene, this.panCentre(whole.x) - this.size / 2);
    renderer.setScissorTest(false);
    renderer.setViewport(0, 0, whole.x, whole.y);
    renderer.autoClear = clears;
  }

  private render(renderer: WebGLRenderer, scene: Scene, left: number): void {
    renderer.setViewport(left, this.margin, this.size, this.size);
    renderer.setScissor(left, this.margin, this.size, this.size);
    renderer.clearDepth();
    renderer.render(scene, this.camera);
  }

  /** Frees what they are drawn from. */
  dispose(): void {
    this.geometries.forEach((geometry) => geometry.dispose());
    [
      this.fill, ...this.rings, ...this.lines, this.yawArrows, this.pitchArrows, this.track,
      this.ballFill, this.ballShine, this.armsX, this.armsY,
    ].forEach((material) => material.dispose());
  }

  private material(colour = RESTING.colour): MeshBasicMaterial {
    return new MeshBasicMaterial({ color: colour, transparent: true, opacity: RESTING.opacity, depthTest: false });
  }

  /** Gives something a geometry that is freed with the rest. */
  private owned<T extends { dispose(): void }>(geometry: T): T {
    this.geometries.push(geometry);
    return geometry;
  }

  private look(handle: SphereHandle): { colour: number; opacity: number } {
    return this.active === handle ? ACTIVE : this.hovered === handle && this.active === null ? HOVERED : RESTING;
  }

  /** Sets each part's colour for what is hovered and active. */
  private paint(): void {
    const set = (material: MeshBasicMaterial, handle: SphereHandle) => {
      const { colour, opacity } = this.look(handle);
      material.color.setHex(colour);
      material.opacity = opacity;
    };
    set(this.yawArrows, "yaw");
    set(this.pitchArrows, "pitch");
    set(this.track, "pan");
    set(this.ballFill, "pan");
    set(this.armsX, "pan-x");
    set(this.armsY, "pan-y");

    // the orbit sphere keeps the axes' colours, and only gains strength and a tint when it is the one in use
    const orbit = this.look("orbit");
    this.fill.color.setHex(orbit === RESTING ? 0x808080 : 0x0969da);
    this.fill.opacity = orbit === RESTING ? 0.1 : 0.22;
    for (const material of [...this.rings, ...this.lines]) {
      material.opacity = orbit === RESTING ? 0.6 : 0.95;
    }
    this.ballShine.opacity = 0.35;
  }

  private buildOrbit(): void {
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
    this.orbitScene.add(this.globe);

    // a pair of arrowheads each side of the sphere, across for a yaw and up and down for a pitch, which stay as they are
    this.arrowheads(this.orbitScene, this.yawArrows, [0, Math.PI]);
    this.arrowheads(this.orbitScene, this.pitchArrows, [Math.PI / 2, -Math.PI / 2]);
  }

  private buildPan(): void {
    const track = new Mesh(this.owned(new RingGeometry(SPHERE_RADIUS - 1, SPHERE_RADIUS + 1, 64)), this.track);
    this.panScene.add(track);

    const reach = ARROW_AT - 2;
    const across = new Mesh(this.owned(new PlaneGeometry(reach * 2, 2)), this.armsX);
    const down = new Mesh(this.owned(new PlaneGeometry(2, reach * 2)), this.armsY);
    this.panScene.add(across, down);
    this.arrowheads(this.panScene, this.armsX, [0, Math.PI]);
    this.arrowheads(this.panScene, this.armsY, [Math.PI / 2, -Math.PI / 2]);

    // a ball is a disc with a lighter disc on its upper left
    const ball = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS, 24)), this.ballFill);
    const shine = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS * 0.4, 16)), this.ballShine);
    shine.position.set(-BALL_RADIUS * 0.35, BALL_RADIUS * 0.35, 1);
    this.ballMark.add(ball, shine);
    this.panScene.add(this.ballMark);
    [ball, shine].forEach((mesh) => (mesh.renderOrder = 5));
  }

  /**
   * Puts an arrowhead at each of some angles round a sphere, pointing away from it.
   *
   * @param scene what to add them to
   * @param material what to make them of
   * @param angles where each goes, counterclockwise from the right
   */
  private arrowheads(scene: Scene, material: MeshBasicMaterial, angles: number[]): void {
    const head = this.owned(new ShapeGeometry(new Shape().moveTo(0, 7).lineTo(-6, -5).lineTo(6, -5).closePath()));
    for (const at of angles) {
      const arrow = new Mesh(head, material);
      arrow.position.set(ARROW_AT * Math.cos(at), ARROW_AT * Math.sin(at), 0);
      // the tip is along +y until it is turned, so a quarter turn back points it away from the centre
      arrow.rotation.z = at - Math.PI / 2;
      arrow.renderOrder = 10;
      scene.add(arrow);
    }
  }
}
