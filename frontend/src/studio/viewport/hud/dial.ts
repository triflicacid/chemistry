import {Mesh, MeshBasicMaterial, type PerspectiveCamera, Scene, Shape, ShapeGeometry} from "three";

import {ARROW_AT} from "./constants";

/** What a press on the camera HUD takes hold of. */
export type HudHandle = "orbit" | "yaw" | "pitch" | "fly" | "fov" | "cone";

/** A point or offset in a dial's own pixels, from its centre, with x to the right and y up. */
export interface HudPoint {
  x: number;
  y: number;
}

/** How a part is drawn. */
export interface Look {
  colour: number;
  opacity: number;
}

export const RESTING: Look = { colour: 0x808080, opacity: 0.35 };
export const HOVERED: Look = { colour: 0x0969da, opacity: 0.8 };
export const ACTIVE: Look = { colour: 0x0969da, opacity: 0.95 };

/** One dial of the camera's controls, which is drawn in a square of its own. */
export abstract class HudDial {
  /** What the dial is drawn from. */
  readonly scene = new Scene();

  private readonly materials: MeshBasicMaterial[] = [];
  private readonly geometries: { dispose(): void }[] = [];

  /** Brings it into line with the camera, which is drawn from whatever the camera is doing now. */
  update(_camera: PerspectiveCamera): void {}

  /**
   * Sets each part's colour for what is hovered and active.
   *
   * @param look how a handle is drawn
   */
  abstract paint(look: (handle: HudHandle) => Look): void;

  /** Frees what it is drawn from. */
  dispose(): void {
    this.geometries.forEach((geometry) => geometry.dispose());
    this.materials.forEach((material) => material.dispose());
  }

  protected material(colour = RESTING.colour): MeshBasicMaterial {
    const material = new MeshBasicMaterial({ color: colour, transparent: true, opacity: RESTING.opacity, depthTest: false });
    this.materials.push(material);
    return material;
  }

  /** Gives something a geometry that is freed with the rest. */
  protected owned<T extends { dispose(): void }>(geometry: T): T {
    this.geometries.push(geometry);
    return geometry;
  }

  /**
   * Puts an arrowhead at each of some angles round a sphere, pointing away from it.
   *
   * @param material what to make them of
   * @param angles where each goes, counterclockwise from the right
   */
  protected arrowheads(material: MeshBasicMaterial, angles: number[]): void {
    const head = this.owned(new ShapeGeometry(new Shape().moveTo(0, 7).lineTo(-6, -5).lineTo(6, -5).closePath()));
    for (const at of angles) {
      const arrow = new Mesh(head, material);
      arrow.position.set(ARROW_AT * Math.cos(at), ARROW_AT * Math.sin(at), 0);
      // the tip is along +y until it is turned, so a quarter turn back points it away from the centre
      arrow.rotation.z = at - Math.PI / 2;
      arrow.renderOrder = 10;
      this.scene.add(arrow);
    }
  }
}
