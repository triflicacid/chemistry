import {CircleGeometry, Group, Mesh, MeshBasicMaterial, RingGeometry, Shape, ShapeGeometry} from "three";

import {BALL_RADIUS, FLY_ARROW_AT, FLY_TRACK, LIFT_MARK_AT, LIFT_RANGE, LIFT_RING} from "./constants";
import {HudDial, type HudHandle, type HudPoint, type Look} from "./dial";

/**
 * A joystick for flying the camera: the ball is pulled out of the middle of a square track, and the camera moves for as
 * long as it is held out, to the right or left and forwards or back, faster the further it is pulled.
 * <p>
 * A ring round the track lifts the camera: turned clockwise from the top it rises, counterclockwise it sinks, and again
 * faster the further it is turned.
 */
export class FlyDial extends HudDial {
  /** Where the ball is, from the centre, in pixels with y up, which the view sets as the stick is pulled. */
  ball: HudPoint = { x: 0, y: 0 };
  /** How far the ring is turned, from -1 to 1 with rising positive, which the view sets as it is turned. */
  lift = 0;

  private readonly ballMark = new Group();
  private readonly track = this.material();
  private readonly ballFill = this.material();
  private readonly ballShine = this.material(0xffffff);
  private readonly arrows = this.material();
  private readonly ring = this.material();
  private readonly knob = this.material();
  private readonly knobMark = new Mesh(this.owned(new CircleGeometry(5, 16)), this.knob);

  constructor() {
    super();
    // a ring of four sides is a square corner to corner, so it is turned and made as wide as that takes
    const corner = Math.SQRT2;
    const track = new Mesh(this.owned(new RingGeometry((FLY_TRACK - 1) * corner, (FLY_TRACK + 1) * corner, 4)), this.track);
    track.rotation.z = Math.PI / 4;
    this.scene.add(track);
    this.arrowheads(this.arrows, [0, Math.PI / 2, Math.PI, -Math.PI / 2], FLY_ARROW_AT);

    this.scene.add(new Mesh(this.owned(new RingGeometry(LIFT_RING - 1, LIFT_RING + 1, 64)), this.ring));
    // clockwise from the top rises, so the up mark is to the right of the top and the down mark to the left of it
    const triangle = this.owned(new ShapeGeometry(new Shape().moveTo(0, 6).lineTo(-5.5, -4).lineTo(5.5, -4).closePath()));
    const rises = Math.PI / 2 - LIFT_MARK_AT;
    const sinks = Math.PI / 2 + LIFT_MARK_AT;
    const up = new Mesh(triangle, this.ring);
    up.position.set(LIFT_RING * Math.cos(rises), LIFT_RING * Math.sin(rises), 0);
    const down = new Mesh(triangle, this.ring);
    down.position.set(LIFT_RING * Math.cos(sinks), LIFT_RING * Math.sin(sinks), 0);
    down.rotation.z = Math.PI;
    this.scene.add(up, down, this.knobMark);
    this.knobMark.renderOrder = 6;

    const ball = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS, 24)), this.ballFill);
    const shine = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS * 0.4, 16)), this.ballShine);
    shine.position.set(-BALL_RADIUS * 0.35, BALL_RADIUS * 0.35, 1);
    this.ballMark.add(ball, shine);
    this.scene.add(this.ballMark);
    [ball, shine].forEach((mesh) => (mesh.renderOrder = 5));
  }

  /**
   * What a press is on, if it is on the ring, the track or its arrowheads.
   *
   * @param dx how far the press is right of the track's centre, in pixels
   * @param dy how far it is above the track's centre, in pixels
   * @returns the handle, or null if the press is not on the dial
   */
  hit(dx: number, dy: number): HudHandle | null {
    const distance = Math.hypot(dx, dy);
    // the ring's band takes in the corners of the track, which it is outside of, and is looked at first
    if (distance >= LIFT_RING - 3 && distance <= LIFT_RING + 4) {
      return "lift";
    }
    const inTrack = Math.abs(dx) <= FLY_TRACK && Math.abs(dy) <= FLY_TRACK;
    const onArrow = distance <= FLY_ARROW_AT + 10 && (Math.abs(dx) <= 10 || Math.abs(dy) <= 10);
    return inTrack || onArrow ? "fly" : null;
  }

  override update(): void {
    this.ballMark.position.set(this.ball.x, this.ball.y, 0);
    const at = Math.PI / 2 - this.lift * LIFT_RANGE;
    this.knobMark.position.set(LIFT_RING * Math.cos(at), LIFT_RING * Math.sin(at), 0);
  }

  paint(look: (handle: HudHandle) => Look): void {
    const stick = look("fly");
    for (const material of [this.track, this.ballFill, this.arrows] as MeshBasicMaterial[]) {
      material.color.setHex(stick.colour);
      material.opacity = stick.opacity;
    }
    const lift = look("lift");
    for (const material of [this.ring, this.knob] as MeshBasicMaterial[]) {
      material.color.setHex(lift.colour);
      material.opacity = lift.opacity;
    }
    this.knob.opacity = Math.min(1, lift.opacity + 0.2);
    this.ballShine.opacity = 0.35;
  }
}
