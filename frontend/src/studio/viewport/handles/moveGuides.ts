import {
  ArrowHelper,
  type BufferGeometry,
  DoubleSide,
  EdgesGeometry,
  Group,
  LineBasicMaterial,
  LineSegments,
  Mesh,
  MeshBasicMaterial,
  PlaneGeometry,
  Vector3,
} from "three";
import type { Constraint } from "./dragPlane";

/** How far a guide reaches from the atom it is drawn on, in angstroms. */
export const GUIDE_REACH = 2.5;

/** The colour of each axis, as the scene's own axes draw them. */
const AXIS_COLOUR = { x: 0xe41a1c, y: 0x2ca02c, z: 0x1f4fd8 };

/** The colour of the plane that free movement is held to, which belongs to no axis. */
const FREE_COLOUR = 0xb0b0b0;

/** How opaque the plane is. It is faint, so that the molecule shows through it. */
const PLANE_OPACITY = 0.16;

/** What a guide drawn for a constraint is made of, and what it is facing. */
export interface Guide {
  /** The guide, centred on the origin for the caller to place on an atom. */
  group: Group;
  /** Whether the guide turns to face the camera, as the plane for free movement does. */
  facesCamera: boolean;
  /** Releases what the guide holds. */
  dispose(): void;
}

/**
 * Draws how a move held to a constraint would go.
 *
 * @param constraint what a move is held to
 * @returns the guide, which is drawn through everything, since it is there to show where an atom can go
 */
export function makeGuide(constraint: Constraint): Guide {
  const group = new Group();
  const geometries: BufferGeometry[] = [];

  // arrow along that direction
  if (constraint === "x" || constraint === "y" || constraint === "z") {
    const direction = new Vector3(constraint === "x" ? 1 : 0, constraint === "y" ? 1 : 0, constraint === "z" ? 1 : 0);
    for (const sign of [1, -1]) {
      const arrow = new ArrowHelper(direction.clone().multiplyScalar(sign), new Vector3(), GUIDE_REACH, AXIS_COLOUR[constraint], 0.45, 0.3);
      overlay(arrow.line.material as LineBasicMaterial, 0.85);
      overlay(arrow.cone.material as MeshBasicMaterial, 0.85);
      arrow.line.renderOrder = arrow.cone.renderOrder = 10;
      group.add(arrow);
    }
    return { group, facesCamera: false, dispose: () => release(group, geometries) };
  }

  // plane
  const colour = constraint === "free" ? FREE_COLOUR : AXIS_COLOUR[normalAxis(constraint)];
  const size = GUIDE_REACH * 2;
  const plane = new PlaneGeometry(size, size);
  const edges = new EdgesGeometry(plane);
  geometries.push(plane, edges);

  const sheet = new Mesh(plane, overlay(new MeshBasicMaterial({ color: colour, side: DoubleSide }), PLANE_OPACITY));
  const outline = new LineSegments(edges, overlay(new LineBasicMaterial({ color: colour }), 0.6));
  sheet.renderOrder = outline.renderOrder = 10;
  group.add(sheet, outline);

  // a plane geometry faces +z, so turn it to face the axis the constraint leaves out
  if (constraint !== "free") {
    const normal = new Vector3(normalAxis(constraint) === "x" ? 1 : 0, normalAxis(constraint) === "y" ? 1 : 0, normalAxis(constraint) === "z" ? 1 : 0);
    group.quaternion.setFromUnitVectors(new Vector3(0, 0, 1), normal);
  }

  return { group, facesCamera: constraint === "free", dispose: () => release(group, geometries) };
}

/** The axis a plane constraint leaves out, which is the plane's normal. */
function normalAxis(constraint: "xy" | "yz" | "xz" | Exclude<Constraint, "x" | "y" | "z">): "x" | "y" | "z" {
  switch (constraint) {
    case "yz":
      return "x";
    case "xz":
      return "y";
    default:
      return "z";
  }
}

/** Makes a material see-through and drawn over whatever is in front of it. */
function overlay<T extends LineBasicMaterial | MeshBasicMaterial>(material: T, opacity: number): T {
  material.transparent = true;
  material.opacity = opacity;
  material.depthTest = false;
  material.depthWrite = false;
  return material;
}

/** Frees the materials in a group and the geometries that were made for it. */
function release(group: Group, geometries: BufferGeometry[]): void {
  group.traverse((object) => {
    const material = (object as Mesh).material;
    if (Array.isArray(material)) {
      material.forEach((each) => each.dispose());
    } else {
      material?.dispose();
    }
  });
  geometries.forEach((geometry) => geometry.dispose());
}
