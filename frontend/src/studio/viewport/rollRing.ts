import {
  Mesh,
  MeshBasicMaterial,
  OrthographicCamera,
  RingGeometry,
  Scene,
  Shape,
  ShapeGeometry,
  Vector2,
  type WebGLRenderer,
} from "three";

/** How far the ring is from its centre, in CSS pixels. */
export const ROLL_RING_RADIUS = 62;

/** How far either side of the ring a press may be and still take hold of it, in CSS pixels. */
export const ROLL_RING_REACH = 11;

/** How far past the ring the square it is drawn in reaches, in CSS pixels. */
const MARGIN = 14;

const RESTING = { colour: 0x808080, opacity: 0.35 };
const HOVERED = { colour: 0x0969da, opacity: 0.8 };
const ACTIVE = { colour: 0x0969da, opacity: 0.95 };

/**
 * Whether a point is on the ring, which is where a press takes hold of it.
 *
 * @param dx how far the point is to the right of the ring's centre, in pixels
 * @param dy how far the point is above the ring's centre, in pixels
 * @returns whether it is within reach of the ring
 */
export function onRing(dx: number, dy: number): boolean {
  return Math.abs(Math.hypot(dx, dy) - ROLL_RING_RADIUS) <= ROLL_RING_REACH;
}

/**
 * Where a point is round the ring.
 *
 * @param dx how far the point is to the right of the ring's centre
 * @param dy how far the point is above the ring's centre
 * @returns the angle in radians, counterclockwise from the right, as the screen shows it
 */
export function angleAround(dx: number, dy: number): number {
  return Math.atan2(dy, dx);
}

/** A faint ring with an arrowhead each way round, drawn in the lower left corner of the canvas. */
export class RollRing {
  /** Whether the pointer is over it, which brightens it. */
  hovered = false;

  /** Whether it is being turned, which brightens it further. */
  active = false;

  private readonly scene = new Scene();
  private readonly camera: OrthographicCamera;
  private readonly material = new MeshBasicMaterial({ color: RESTING.colour, transparent: true, opacity: RESTING.opacity, depthTest: false });
  private readonly meshes: Mesh[] = [];
  private readonly half = ROLL_RING_RADIUS + MARGIN;

  /**
   * @param centre how far the ring's centre is from the canvas's left and bottom edges, in CSS pixels, which must be at
   *     least {@link ROLL_RING_RADIUS} plus its margin so that the whole ring fits
   */
  constructor(private readonly centre: number) {
    this.camera = new OrthographicCamera(-this.half, this.half, this.half, -this.half, -1, 1);

    const ring = new Mesh(new RingGeometry(ROLL_RING_RADIUS - 1.5, ROLL_RING_RADIUS + 1.5, 96), this.material);
    this.scene.add(ring);
    this.meshes.push(ring);

    // the arrowheads sit either side of the top, each pointing away from it, one way round and the other
    const head = new Shape().moveTo(0, 7).lineTo(-6, -5).lineTo(6, -5).closePath();
    [
      { at: (125 * Math.PI) / 180, turn: 0 },
      { at: (55 * Math.PI) / 180, turn: Math.PI },
    ].forEach(({ at, turn }) => {
      const arrow = new Mesh(new ShapeGeometry(head), this.material);
      arrow.position.set(ROLL_RING_RADIUS * Math.cos(at), ROLL_RING_RADIUS * Math.sin(at), 0);
      // the tip points along the way round the ring, which at an angle `at` is at a right angle to it
      arrow.rotation.z = at + turn;
      this.scene.add(arrow);
      this.meshes.push(arrow);
    });
  }

  /**
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   * @returns how far the pointer is right of the ring's centre and above it, in pixels
   */
  offsetOf(canvas: DOMRect, x: number, y: number): { dx: number; dy: number } {
    return { dx: x - (canvas.left + this.centre), dy: canvas.bottom - this.centre - y };
  }

  /**
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   * @returns whether the pointer is on the ring
   */
  hit(canvas: DOMRect, x: number, y: number): boolean {
    const { dx, dy } = this.offsetOf(canvas, x, y);
    return onRing(dx, dy);
  }

  /**
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   * @returns the angle the pointer is at round the ring, counterclockwise from the right
   */
  angleAt(canvas: DOMRect, x: number, y: number): number {
    const { dx, dy } = this.offsetOf(canvas, x, y);
    return angleAround(dx, dy);
  }

  /**
   * Draws the ring over what the renderer has already drawn.
   *
   * @param renderer the renderer, which has just drawn the scene
   */
  draw(renderer: WebGLRenderer): void {
    const look = this.active ? ACTIVE : this.hovered ? HOVERED : RESTING;
    this.material.color.setHex(look.colour);
    this.material.opacity = look.opacity;

    const side = this.half * 2;
    const left = this.centre - this.half;
    const whole = renderer.getSize(new Vector2());
    const clears = renderer.autoClear;

    renderer.autoClear = false;
    renderer.setScissorTest(true);
    renderer.setViewport(left, left, side, side);
    renderer.setScissor(left, left, side, side);
    renderer.render(this.scene, this.camera);

    renderer.setScissorTest(false);
    renderer.setViewport(0, 0, whole.x, whole.y);
    renderer.autoClear = clears;
  }

  /** Frees what the ring is drawn from. */
  dispose(): void {
    this.meshes.forEach((mesh) => mesh.geometry.dispose());
    this.material.dispose();
  }
}
