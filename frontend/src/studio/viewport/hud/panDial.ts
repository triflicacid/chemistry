import {CircleGeometry, Group, Mesh, MeshBasicMaterial, PlaneGeometry, RingGeometry} from "three";

import {ARROW_AT, ARROW_REACH, SPHERE_RADIUS} from "./constants";
import {HudDial, type HudHandle, type HudPoint, type Look} from "./dial";

/** The radius of the pan ball, in CSS pixels. */
export const BALL_RADIUS = 12;

/** How far the pan ball can be from the centre, which keeps it inside its track. */
export const BALL_TRAVEL = SPHERE_RADIUS - BALL_RADIUS;

/** How far either side of an arm's line a press may be and still take hold of it, in CSS pixels. */
const ARM_REACH = 6;

/**
 * A ball that is pulled out of the middle of its track as the view is panned, and let go of back to the middle, and arms
 * that hold a pan across or up and down.
 */
export class PanDial extends HudDial {
  /** Where the ball is, from the centre, in pixels with y up, which the view sets as it pans. */
  ball: HudPoint = { x: 0, y: 0 };

  private readonly ballMark = new Group();
  private readonly track = this.material();
  private readonly ballFill = this.material();
  private readonly ballShine = this.material(0xffffff);
  private readonly armsX = this.material();
  private readonly armsY = this.material();

  constructor() {
    super();
    this.scene.add(new Mesh(this.owned(new RingGeometry(SPHERE_RADIUS - 1, SPHERE_RADIUS + 1, 64)), this.track));

    const reach = ARROW_AT - 2;
    const across = new Mesh(this.owned(new PlaneGeometry(reach * 2, 2)), this.armsX);
    const down = new Mesh(this.owned(new PlaneGeometry(2, reach * 2)), this.armsY);
    this.scene.add(across, down);
    this.arrowheads(this.armsX, [0, Math.PI]);
    this.arrowheads(this.armsY, [Math.PI / 2, -Math.PI / 2]);

    // a ball is a disc with a lighter disc on its upper left
    const ball = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS, 24)), this.ballFill);
    const shine = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS * 0.4, 16)), this.ballShine);
    shine.position.set(-BALL_RADIUS * 0.35, BALL_RADIUS * 0.35, 1);
    this.ballMark.add(ball, shine);
    this.scene.add(this.ballMark);
    [ball, shine].forEach((mesh) => (mesh.renderOrder = 5));
  }

  /**
   * What a press is on, if it is on the sphere. The ball comes first, then the arms, and anywhere else in the track moves
   * the view any way.
   *
   * @param dx how far the press is right of the sphere's centre, in pixels
   * @param dy how far it is above the sphere's centre, in pixels
   * @returns the handle, or null if the press is not on the sphere or its arms
   */
  hit(dx: number, dy: number): HudHandle | null {
    if (Math.hypot(dx - this.ball.x, dy - this.ball.y) <= BALL_RADIUS + 3) {
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

  override update(): void {
    this.ballMark.position.set(this.ball.x, this.ball.y, 0);
  }

  paint(look: (handle: HudHandle) => Look): void {
    const set = (material: MeshBasicMaterial, handle: HudHandle) => {
      const { colour, opacity } = look(handle);
      material.color.setHex(colour);
      material.opacity = opacity;
    };
    set(this.track, "pan");
    set(this.ballFill, "pan");
    set(this.armsX, "pan-x");
    set(this.armsY, "pan-y");
    this.ballShine.opacity = 0.35;
  }
}
