import {CircleGeometry, Group, Mesh, MeshBasicMaterial, RingGeometry} from "three";

import {ARROW_AT, BALL_RADIUS, SPHERE_RADIUS} from "./constants";
import {HudDial, type HudHandle, type HudPoint, type Look} from "./dial";

/**
 * A joystick for flying the camera: the ball is pulled out of the middle of a square track, and the camera moves for as
 * long as it is held out, to the right or left and forwards or back, faster the further it is pulled.
 */
export class FlyDial extends HudDial {
  /** Where the ball is, from the centre, in pixels with y up, which the view sets as the stick is pulled. */
  ball: HudPoint = { x: 0, y: 0 };

  private readonly ballMark = new Group();
  private readonly track = this.material();
  private readonly ballFill = this.material();
  private readonly ballShine = this.material(0xffffff);
  private readonly arrows = this.material();

  constructor() {
    super();
    // a ring of four sides is a square corner to corner, so it is turned and made as wide as that takes
    const corner = Math.SQRT2;
    const track = new Mesh(this.owned(new RingGeometry((SPHERE_RADIUS - 1) * corner, (SPHERE_RADIUS + 1) * corner, 4)), this.track);
    track.rotation.z = Math.PI / 4;
    this.scene.add(track);
    this.arrowheads(this.arrows, [0, Math.PI / 2, Math.PI, -Math.PI / 2]);

    const ball = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS, 24)), this.ballFill);
    const shine = new Mesh(this.owned(new CircleGeometry(BALL_RADIUS * 0.4, 16)), this.ballShine);
    shine.position.set(-BALL_RADIUS * 0.35, BALL_RADIUS * 0.35, 1);
    this.ballMark.add(ball, shine);
    this.scene.add(this.ballMark);
    [ball, shine].forEach((mesh) => (mesh.renderOrder = 5));
  }

  /**
   * What a press is on, if it is on the track or its arrowheads.
   *
   * @param dx how far the press is right of the track's centre, in pixels
   * @param dy how far it is above the track's centre, in pixels
   * @returns the handle, or null if the press is not on the dial
   */
  hit(dx: number, dy: number): HudHandle | null {
    const inTrack = Math.abs(dx) <= SPHERE_RADIUS && Math.abs(dy) <= SPHERE_RADIUS;
    const onArrow = Math.hypot(dx, dy) <= ARROW_AT + 10 && (Math.abs(dx) <= 10 || Math.abs(dy) <= 10);
    return inTrack || onArrow ? "fly" : null;
  }

  override update(): void {
    this.ballMark.position.set(this.ball.x, this.ball.y, 0);
  }

  paint(look: (handle: HudHandle) => Look): void {
    const { colour, opacity } = look("fly");
    for (const material of [this.track, this.ballFill, this.arrows] as MeshBasicMaterial[]) {
      material.color.setHex(colour);
      material.opacity = opacity;
    }
    this.ballShine.opacity = 0.35;
  }
}
