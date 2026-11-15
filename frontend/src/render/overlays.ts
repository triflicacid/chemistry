import { Matrix4, Mesh, MeshBasicMaterial, Object3D, Quaternion, Sprite, Vector3 } from "three";
import type { Conjugation3D } from "../api/types/structure";
import { UP, type Primitives } from "./primitives";

/** The colour of everything drawn for a pi system. */
export const PI_COLOUR = "#16a085";

/** How wide a pi lobe is drawn, and how much wider each electron it holds draws it, in angstroms. */
const PI_LOBE_RADIUS = 0.08;
const PI_LOBE_PER_ELECTRON = 0.04;
/** How far along the way to its tip a lobe starts, leaving the nucleus clear. */
const PI_LOBE_START = 0.15;
/** How wide and how thick the lozenge of a pi cloud is drawn, in angstroms. */
const PI_CLOUD_WIDTH = 0.22;
const PI_CLOUD_THICKNESS = 0.1;

/**
 * Builds a system's pi cloud.
 *
 * @param kit the shared geometry to build from
 * @param system the pi system
 * @param point where a site is, by site index
 * @param shown whether each site, by index, is drawn; a cloud over a bond with a hidden end is left out
 * @returns the lozenges, for the caller to add to the scene
 */
export function piCloud(kit: Primitives, system: Conjugation3D, point: (index: number) => Vector3, shown: boolean[]): Mesh[] {
  const blobs: Mesh[] = [];
  for (const cloud of system.clouds) {
    if (!shown[cloud.first] || !shown[cloud.second]) {
      continue;
    }
    const above = new Vector3(cloud.above.x, cloud.above.y, cloud.above.z);
    const below = new Vector3(cloud.below.x, cloud.below.y, cloud.below.z);
    // the lozenge runs the length of its bond, so one meets the next around a ring
    const along = point(cloud.second).clone().sub(point(cloud.first)).multiplyScalar(0.5);
    const normal = above.clone().sub(below);
    for (const face of [above, below]) {
      blobs.push(blob(kit, face, along, normal, PI_CLOUD_WIDTH, PI_CLOUD_THICKNESS));
    }
  }
  return blobs;
}

/**
 * Builds the p orbital standing on each atom of a system.
 *
 * @param kit the shared geometry to build from
 * @param system the pi system
 * @param point where a site is, by site index
 * @param shown whether each site, by index, is drawn; a hidden atom's lobes are left out
 * @returns the lobes, for the caller to add to the scene
 */
export function piOrbitals(kit: Primitives, system: Conjugation3D, point: (index: number) => Vector3, shown: boolean[]): Mesh[] {
  const lobes: Mesh[] = [];
  for (const orbital of system.orbitals) {
    if (!shown[orbital.node]) {
      continue;
    }
    const nucleus = point(orbital.node);
    const width = PI_LOBE_RADIUS + PI_LOBE_PER_ELECTRON * orbital.electrons;
    for (const tip of [orbital.above, orbital.below]) {
      const reach = new Vector3(tip.x, tip.y, tip.z).sub(nucleus);
      const along = reach.clone().multiplyScalar((1 - PI_LOBE_START) / 2);
      const centre = nucleus.clone().addScaledVector(reach, PI_LOBE_START).add(along);
      lobes.push(blob(kit, centre, along, null, width, width));
    }
  }
  return lobes;
}

/**
 * Builds a label saying what a system amounts to in the middle of its atoms.
 *
 * @param kit the shared geometry and labels to build from
 * @param system the pi system
 * @param point where a site is, by site index
 * @param shown whether each site, by index, is drawn
 * @returns the label, or null if none of the system's atoms are drawn
 */
export function piLabel(kit: Primitives, system: Conjugation3D, point: (index: number) => Vector3, shown: boolean[]): Sprite | null {
  const nodes = system.orbitals.filter((orbital) => shown[orbital.node]);
  if (nodes.length === 0) {
    return null;
  }
  const middle = new Vector3();
  nodes.forEach((orbital) => middle.add(point(orbital.node)));

  const note = kit.labels.label(`${system.aromaticity.toLowerCase()} · ${system.pi_electrons} π`, PI_COLOUR, 0.26, true);
  note.position.copy(middle.divideScalar(nodes.length));
  return note;
}

/**
 * Builds an ellipsoid for a pi overlay.
 *
 * @param kit the shared geometry to build from
 * @param centre the middle of the ellipsoid
 * @param along half its long axis, as a vector
 * @param normal the direction the ellipsoid is flattened along, or null for a round lobe
 * @param width half the short axis across `along`
 * @param thickness half the axis along `normal`
 * @returns a translucent mesh
 */
export function blob(kit: Primitives, centre: Vector3, along: Vector3, normal: Vector3 | null, width: number, thickness: number): Mesh {
  const mesh = new Mesh(
    kit.sphereGeometry,
    new MeshBasicMaterial({ color: PI_COLOUR, transparent: true, opacity: 0.3, depthWrite: false }),
  );
  const length = along.length();
  mesh.position.copy(centre);
  if (length > 1e-9) {
    const y = along.clone().normalize();
    const z = (normal ?? sideways(y)).clone().addScaledVector(y, -(normal ?? sideways(y)).dot(y));
    if (z.lengthSq() > 1e-12) {
      z.normalize();
      mesh.quaternion.setFromRotationMatrix(new Matrix4().makeBasis(new Vector3().crossVectors(y, z), y, z));
    } else {
      mesh.quaternion.copy(new Quaternion().setFromUnitVectors(UP, y));
    }
  }
  mesh.scale.set(width, Math.max(length, 1e-6), thickness);
  return mesh;
}

/** Finds a direction at right angles to `axis`. */
function sideways(axis: Vector3): Vector3 {
  const helper = Math.abs(axis.x) < 0.9 ? new Vector3(1, 0, 0) : new Vector3(0, 1, 0);
  return new Vector3().crossVectors(axis, helper).normalize();
}

/**
 * Builds an arc between two bonds at `middle`, labelled with the angle.
 *
 * @param kit the shared geometry and labels to build from
 * @param middle the atom at the vertex of the angle
 * @param a an atom bonded to the vertex
 * @param b another atom bonded to the vertex
 * @param text what the label says
 * @returns the arc's segments and its label, or nothing if the bonds are collinear and there is no arc to draw
 */
export function angleArc(kit: Primitives, middle: Vector3, a: Vector3, b: Vector3, text: string): Object3D[] {
  const u = a.clone().sub(middle).normalize();
  const v = b.clone().sub(middle).normalize();
  const sweep = Math.acos(Math.min(1, Math.max(-1, u.dot(v))));
  const w = v.clone().addScaledVector(u, -v.dot(u));
  if (w.lengthSq() < 1e-9) {
    return [];
  }
  w.normalize();

  const at = (fraction: number, radius: number) =>
    middle.clone()
      .addScaledVector(u, Math.cos(sweep * fraction) * radius)
      .addScaledVector(w, Math.sin(sweep * fraction) * radius);
  const parts: Object3D[] = [];
  const steps = 16;
  for (let i = 0; i < steps; i++) {
    parts.push(
      kit.cylinder(at(i / steps, 0.45), at((i + 1) / steps, 0.45), 0.02, new MeshBasicMaterial({ color: "#d35400", depthTest: false })),
    );
  }
  const note = kit.labels.label(text, "#a04000", 0.22);
  note.position.copy(at(0.5, 0.7));
  parts.push(note);
  return parts;
}
