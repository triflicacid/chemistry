import {CircleGeometry, Mesh, MeshBasicMaterial, type PerspectiveCamera, PlaneGeometry, RingGeometry} from "three";

import {MAX_FOV, MIN_FOV} from "../../selection/CameraModel";
import {HudDial, type HudHandle, type HudPoint, type Look, RESTING} from "./dial";

/** Where the camera dot is, from the centre, with the cone opening upwards from it. */
const APEX: HudPoint = { x: 0, y: -18 };

/** How far the cone reaches from the camera dot, in CSS pixels. */
const CONE_REACH = 58;

/** How high the slider's track is, from the centre, in CSS pixels. */
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
 * @returns how far right of the centre the handle is, in CSS pixels
 */
export function sliderOffsetOfFov(fov: number): number {
  const clamped = Math.max(MIN_FOV, Math.min(MAX_FOV, fov));
  const fraction = Math.log(clamped / MIN_FOV) / Math.log(MAX_FOV / MIN_FOV);
  return (fraction * 2 - 1) * SLIDER_HALF;
}

/**
 * The field of view the slider's handle gives at a place; the inverse of {@link sliderOffsetOfFov}.
 *
 * @param dx how far right of the centre the pointer is, in CSS pixels
 * @returns the field of view in degrees, kept between the least and greatest the camera allows
 */
export function fovOfSliderOffset(dx: number): number {
  const fraction = (Math.max(-SLIDER_HALF, Math.min(SLIDER_HALF, dx)) / SLIDER_HALF + 1) / 2;
  return MIN_FOV * Math.pow(MAX_FOV / MIN_FOV, fraction);
}

/**
 * A camera dot, a cone opening upwards from it as wide as the field of view, and a slider below that sets the width. Either
 * the cone or the slider can be dragged.
 */
export class FovDial extends HudDial {
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

  constructor() {
    super();
    this.coneSector.position.set(APEX.x, APEX.y, 0);
    this.coneArc.position.set(APEX.x, APEX.y, 0);
    this.coneSector.renderOrder = 0;
    this.coneArc.renderOrder = 1;
    this.coneFill.depthWrite = false;
    this.scene.add(this.coneSector, this.coneArc, ...this.coneSides);

    const dot = new Mesh(this.owned(new CircleGeometry(4.5, 16)), this.coneDot);
    dot.position.set(APEX.x, APEX.y, 0);
    dot.renderOrder = 5;
    this.scene.add(dot);

    const track = new Mesh(this.owned(new PlaneGeometry(SLIDER_HALF * 2, 2)), this.sliderTrack);
    track.position.set(0, SLIDER_Y, 0);
    this.handleMark.position.set(0, SLIDER_Y, 1);
    this.handleMark.renderOrder = 5;
    this.scene.add(track, this.handleMark);
  }

  /**
   * What a press is on, if it is on the dial.
   *
   * @param dx how far the press is right of the dial's centre, in pixels
   * @param dy how far it is above the dial's centre, in pixels
   * @returns {@code "fov"} on the slider's track or handle, {@code "cone"} on the cone, otherwise null
   */
  hit(dx: number, dy: number): HudHandle | null {
    if (Math.abs(dy - SLIDER_Y) <= SLIDER_HANDLE + 3 && Math.abs(dx) <= SLIDER_HALF + SLIDER_HANDLE + 2) {
      return "fov";
    }
    return this.onCone(dx, dy) ? "cone" : null;
  }

  /** Whether a press is on the cone, which includes a little beyond its edges so that a thin one can be taken hold of. */
  private onCone(dx: number, dy: number): boolean {
    const x = dx - APEX.x;
    const y = dy - APEX.y;
    const distance = Math.hypot(x, y);
    if (y < -4 || distance > CONE_REACH + 4) {
      return false;
    }
    const half = (this.fov * Math.PI) / 360;
    const slack = Math.atan2(5, Math.max(distance, 5));
    return Math.atan2(Math.abs(x), y) <= half + slack;
  }

  /**
   * The field of view that makes the cone's edge pass through a point: the farther the point is from the cone's middle
   * line, the wider the cone.
   *
   * @param point where the pointer is, from the dial's centre
   * @returns the field of view in degrees, kept between the least and greatest the camera allows
   */
  fovOfCone(point: HudPoint): number {
    const angle = Math.atan2(Math.abs(point.x - APEX.x), point.y - APEX.y);
    return Math.max(MIN_FOV, Math.min(MAX_FOV, (angle * 360) / Math.PI));
  }

  /** Sets the cone and the slider's handle for the camera's field of view. */
  override update(camera: PerspectiveCamera): void {
    const fov = camera.fov;
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
      mesh.position.set(APEX.x + Math.sin(turn) * CONE_REACH / 2, APEX.y + Math.cos(turn) * CONE_REACH / 2, 0);
      mesh.renderOrder = 1;
    });
  }

  paint(look: (handle: HudHandle) => Look): void {
    const set = (material: MeshBasicMaterial, handle: HudHandle) => {
      const { colour, opacity } = look(handle);
      material.color.setHex(colour);
      material.opacity = opacity;
    };
    set(this.coneEdges, "cone");
    set(this.sliderTrack, "fov");
    set(this.sliderHandle, "fov");
    const resting = look("cone") === RESTING;
    this.coneFill.color.setHex(resting ? 0x808080 : 0x0969da);
    this.coneFill.opacity = resting ? 0.12 : 0.22;
  }

  override dispose(): void {
    super.dispose();
    [this.coneSector, this.coneArc, this.handleMark, ...this.coneSides].forEach((mesh) => mesh.geometry.dispose());
  }
}
