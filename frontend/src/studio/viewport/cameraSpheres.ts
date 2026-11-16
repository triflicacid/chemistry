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

import {MAX_FOV, MIN_FOV} from "../selection/CameraModel";

/**
 * What a press on the camera spheres takes hold of: the orbit sphere's body, which turns the view any way, or its
 * arrowheads, which turn it only across (yaw) or only up and down (pitch); the pan sphere's ball, which moves the view any
 * way, or its arms, which move it only across or only up and down; or the field-of-view slider's track and handle.
 */
export type SphereHandle = "orbit" | "yaw" | "pitch" | "pan" | "pan-x" | "pan-y" | "fov" | "cone";

/** A point or offset in a sphere's own pixels, from its centre, with x to the right and y up. */
export interface SpherePoint {
  x: number;
  y: number;
}

/** The radius of the orbit sphere, and of the track the pan ball moves in, in CSS pixels. */
export const SPHERE_RADIUS = 34;

/** Where the camera dot is in the field-of-view widget, from its centre, with the cone opening upwards from it. */
const FOV_APEX: SpherePoint = { x: 0, y: -18 };

/** How far the cone reaches from the camera dot, in CSS pixels. */
const CONE_REACH = 58;

/** How high the slider's track is, from the widget's centre, in CSS pixels. */
const SLIDER_Y = -42;

/** How far the slider's track runs either side of the centre, in CSS pixels. */
const SLIDER_HALF = 40;

/** The radius of the slider's handle, in CSS pixels. */
const SLIDER_HANDLE = 6;

/**
 * Where the slider's handle is for a field of view. The scale is logarithmic, so that a step of the handle changes the view
 * by about as much wherever it is.
 *
 * @param fov the vertical field of view, in degrees
 * @returns how far right of the widget's centre the handle is, in CSS pixels
 */
export function sliderOffsetOfFov(fov: number): number {
  const clamped = Math.max(MIN_FOV, Math.min(MAX_FOV, fov));
  const fraction = Math.log(clamped / MIN_FOV) / Math.log(MAX_FOV / MIN_FOV);
  return (fraction * 2 - 1) * SLIDER_HALF;
}

/**
 * The field of view the slider's handle gives at a place; the inverse of {@link sliderOffsetOfFov}.
 *
 * @param dx how far right of the widget's centre the pointer is, in CSS pixels
 * @returns the field of view in degrees, kept between the least and greatest the camera allows
 */
export function fovOfSliderOffset(dx: number): number {
  const fraction = (Math.max(-SLIDER_HALF, Math.min(SLIDER_HALF, dx)) / SLIDER_HALF + 1) / 2;
  return MIN_FOV * Math.pow(MAX_FOV / MIN_FOV, fraction);
}

/**
 * What the field-of-view widget is being pressed on, if the press is on it.
 *
 * @param dx how far the press is right of the widget's centre, in pixels
 * @param dy how far it is above the widget's centre, in pixels
 * @returns {@code "fov"} if the press is on the slider's track or handle, otherwise null
 */
export function fovHandleAt(dx: number, dy: number): SphereHandle | null {
  return Math.abs(dy - SLIDER_Y) <= SLIDER_HANDLE + 3 && Math.abs(dx) <= SLIDER_HALF + SLIDER_HANDLE + 2 ? "fov" : null;
}

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
  private readonly fovScene = new Scene();
  /** What turns as the camera does. */
  private readonly globe = new Group();
  private readonly ballMark = new Group();

  private readonly coneFill = this.material();
  private readonly coneEdges = this.material();
  private readonly coneDot = this.material();
  private readonly sliderTrack = this.material();
  private readonly sliderHandle = this.material();
  private readonly coneSector = new Mesh(new CircleGeometry(1, 3), this.coneFill);
  private readonly coneArc: Mesh = new Mesh(new CircleGeometry(1, 3), this.coneEdges);
  private readonly coneSides = [new Mesh(new PlaneGeometry(1.5, CONE_REACH), this.coneEdges), new Mesh(new PlaneGeometry(1.5, CONE_REACH), this.coneEdges)];
  private readonly handleMark = new Mesh(new CircleGeometry(SLIDER_HANDLE, 16), this.sliderHandle);
  /** The field of view the cone is built for, or null if it has not been built. */
  private coneBuiltFor: number | null = null;
  /** The field of view last drawn, which says how wide the cone is to be pressed on. */
  private fov = 50;

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
    this.buildFov();
  }

  /**
   * The field of view a pointer at a place on the slider asks for.
   *
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @returns the field of view in degrees, which is the end's if the pointer is beyond the track
   */
  fovAt(canvas: DOMRect, x: number): number {
    return fovOfSliderOffset(x - canvas.left - this.fovCentre(canvas.width));
  }

  /**
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   * @returns what the pointer is over, or null if they are hidden or it is over none
   */
  hit(canvas: DOMRect, x: number, y: number): SphereHandle | null {
    if (!this.visible) {
      return null;
    }
    const orbit = this.offsetFrom(canvas, x, y, this.orbitCentre(canvas.width));
    const pan = this.offsetFrom(canvas, x, y, this.panCentre(canvas.width));
    const fov = this.offsetFrom(canvas, x, y, this.fovCentre(canvas.width));
    return orbitHandleAt(orbit.x, orbit.y) ?? panHandleAt(pan.x, pan.y, this.ball) ?? fovHandleAt(fov.x, fov.y) ?? this.coneHandleAt(fov.x, fov.y);
  }

  /** Whether a press is on the cone, which includes a little beyond its edges so that a thin one can be taken hold of. */
  private coneHandleAt(dx: number, dy: number): SphereHandle | null {
    const x = dx - FOV_APEX.x;
    const y = dy - FOV_APEX.y;
    const distance = Math.hypot(x, y);
    if (y < -4 || distance > CONE_REACH + 4) {
      return null;
    }
    const half = (this.fov * Math.PI) / 360;
    const slack = Math.atan2(5, Math.max(distance, 5));
    return Math.atan2(Math.abs(x), y) <= half + slack ? "cone" : null;
  }

  /**
   * The field of view that makes the cone's edge pass through the pointer: the farther the pointer is from the cone's
   * middle line, the wider the cone.
   *
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   */
  fovOfCone(canvas: DOMRect, x: number, y: number): number {
    const point = this.offsetFrom(canvas, x, y, this.fovCentre(canvas.width));
    const angle = Math.atan2(Math.abs(point.x - FOV_APEX.x), point.y - FOV_APEX.y);
    return Math.max(MIN_FOV, Math.min(MAX_FOV, (angle * 360) / Math.PI));
  }

  /** The distance between the widgets' centres, which are in a row. */
  private get spacing(): number {
    return this.size + 8;
  }

  private orbitCentre(width: number): number {
    return width - this.margin - this.size / 2;
  }

  private panCentre(width: number): number {
    return this.orbitCentre(width) - this.spacing;
  }

  private fovCentre(width: number): number {
    return this.panCentre(width) - this.spacing;
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
    this.shapeCone(from.fov);
    this.paint();

    const whole = renderer.getSize(new Vector2());
    const clears = renderer.autoClear;
    renderer.autoClear = false;
    renderer.setScissorTest(true);
    this.render(renderer, this.orbitScene, this.orbitCentre(whole.x) - this.size / 2);
    this.render(renderer, this.panScene, this.panCentre(whole.x) - this.size / 2);
    this.render(renderer, this.fovScene, this.fovCentre(whole.x) - this.size / 2);
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
    [this.coneSector, this.coneArc, this.handleMark, ...this.coneSides].forEach((mesh) => mesh.geometry.dispose());
    [
      this.fill, ...this.rings, ...this.lines, this.yawArrows, this.pitchArrows, this.track,
      this.ballFill, this.ballShine, this.armsX, this.armsY,
      this.coneFill, this.coneEdges, this.coneDot, this.sliderTrack, this.sliderHandle,
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
    set(this.coneEdges, "cone");
    set(this.sliderTrack, "fov");
    set(this.sliderHandle, "fov");
    this.coneFill.color.setHex(this.look("cone") === RESTING ? 0x808080 : 0x0969da);
    this.coneFill.opacity = this.look("cone") === RESTING ? 0.12 : 0.22;

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

  /** A camera dot, a cone opening upwards from it as wide as the field of view, and a slider below that sets the width. */
  private buildFov(): void {
    this.coneSector.position.set(FOV_APEX.x, FOV_APEX.y, 0);
    this.coneArc.position.set(FOV_APEX.x, FOV_APEX.y, 0);
    this.coneSector.renderOrder = 0;
    this.coneArc.renderOrder = 1;
    this.coneFill.depthWrite = false;
    this.fovScene.add(this.coneSector, this.coneArc, ...this.coneSides);

    const dot = new Mesh(this.owned(new CircleGeometry(4.5, 16)), this.coneDot);
    dot.position.set(FOV_APEX.x, FOV_APEX.y, 0);
    dot.renderOrder = 5;
    this.fovScene.add(dot);

    const track = new Mesh(this.owned(new PlaneGeometry(SLIDER_HALF * 2, 2)), this.sliderTrack);
    track.position.set(0, SLIDER_Y, 0);
    this.handleMark.position.set(0, SLIDER_Y, 1);
    this.handleMark.renderOrder = 5;
    this.fovScene.add(track, this.handleMark);
  }

  /**
   * Sets the cone and the slider's handle for a field of view.
   *
   * @param fov the camera's vertical field of view, in degrees
   */
  private shapeCone(fov: number): void {
    this.fov = fov;
    this.handleMark.position.x = sliderOffsetOfFov(fov);
    if (this.coneBuiltFor === fov) {
      return;
    }
    this.coneBuiltFor = fov;

    const half = (Math.max(MIN_FOV, Math.min(MAX_FOV, fov)) * Math.PI) / 360;
    const start = Math.PI / 2 - half;
    this.coneSector.geometry.dispose();
    this.coneSector.geometry = new CircleGeometry(CONE_REACH, 48, start, half * 2);
    this.coneArc.geometry.dispose();
    this.coneArc.geometry = new RingGeometry(CONE_REACH - 1.5, CONE_REACH, 48, 1, start, half * 2);

    // each side is a strip along y, turned from pointing up to pointing along its edge of the cone
    [-1, 1].forEach((side, i) => {
      const turn = side * half;
      const mesh = this.coneSides[i] as Mesh;
      mesh.rotation.z = -turn;
      mesh.position.set(FOV_APEX.x + Math.sin(turn) * CONE_REACH / 2, FOV_APEX.y + Math.cos(turn) * CONE_REACH / 2, 0);
      mesh.renderOrder = 1;
    });
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
