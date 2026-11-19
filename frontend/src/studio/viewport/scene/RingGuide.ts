import {
  CylinderGeometry,
  DoubleSide,
  Group,
  IcosahedronGeometry,
  Mesh,
  MeshBasicMaterial,
  type Object3D,
  Quaternion,
  SphereGeometry,
  Vector3,
} from "three";
import type {Stage} from "../../../render/stage";
import type {RingLayout} from "../../geometry/ring";

const COLOUR = 0x1f9d8b;
const HOVER_COLOUR = 0xd4a017;

/** How big a ghost atom is, in angstroms. */
const GHOST_RADIUS = 0.3;

/** How thick a ghost bond is, in angstroms. */
const BOND_RADIUS = 0.08;

/** The way a cylinder of three.js points before it is turned. */
const ALONG = new Vector3(0, 1, 0);

/** What a place on the ring is. */
export type Ghost =
  /** Where the next atom can go. */
  | "next"
  /** Where an atom will be able to go later. */
  | "later"
  /** Where there is an atom already, which needs no ghost. */
  | "taken";

/** How one ghost is drawn. */
type Look = "next" | "later" | "hover";

interface GhostAtom {
  body: Mesh;
  shell: Mesh;
}

/** A polygon of ghost atoms to aid in building a ring structure. */
export class RingGuide {
  private readonly root = new Group();
  private readonly sphere = new SphereGeometry(GHOST_RADIUS, 20, 14);
  private readonly shellGeometry = new IcosahedronGeometry(GHOST_RADIUS * 1.12, 1);
  private readonly rod = new CylinderGeometry(1, 1, 1, 14, 1, true);
  private readonly atoms: GhostAtom[] = [];
  private readonly bonds: Mesh[] = [];
  private readonly bodies: Record<Look, MeshBasicMaterial> = {
    next: this.glass(COLOUR, 0.5),
    later: this.glass(COLOUR, 0.18),
    hover: this.glass(HOVER_COLOUR, 0.65),
  };
  private readonly shells: Record<Look, MeshBasicMaterial> = {
    next: this.wire(COLOUR, 0.7),
    later: this.wire(COLOUR, 0.3),
    hover: this.wire(HOVER_COLOUR, 0.9),
  };
  private readonly turn = new Quaternion();

  /**
   * @param stage the stage the guide is drawn on
   */
  constructor(private readonly stage: Stage) {
    this.root.visible = false;
  }

  /**
   * Draws the ring.
   *
   * @param parent what to draw it in, which is the molecule, so that it turns and moves with it
   * @param layout the ring, in the molecule's own frame
   * @param ghosts what each of the ring's places is
   * @param hovered the place the pointer is over, or null for none
   */
  show(parent: Object3D, layout: RingLayout, ghosts: readonly Ghost[], hovered: number | null): void {
    // rebuilding the molecule clears what it holds, so the guide is put back in whenever it is drawn
    if (this.root.parent !== parent) {
      parent.add(this.root);
    }

    const size = layout.vertices.length;
    layout.vertices.forEach((vertex, place) => {
      const ghost = this.atomAt(place);
      const state = ghosts[place] ?? "taken";
      const look: Look = place === hovered && state === "next" ? "hover" : state === "next" ? "next" : "later";
      ghost.body.visible = state !== "taken";
      ghost.shell.visible = state !== "taken";
      ghost.body.material = this.bodies[look];
      ghost.shell.material = this.shells[look];
      ghost.body.position.copy(vertex);
      ghost.shell.position.copy(vertex);
    });

    // each side of the polygon is a bond, except one between two atoms that are there already
    for (let place = 0; place < size; place++) {
      const after = (place + 1) % size;
      const bond = this.bondAt(place);
      const from = ghosts[place] ?? "taken";
      const to = ghosts[after] ?? "taken";
      bond.visible = from !== "taken" || to !== "taken";

      // the bond the next atom would make is more solid then the others
      const making = (from === "taken" && to === "next") || (from === "next" && to === "taken");
      bond.material = this.bodies[making ? "next" : "later"];

      const start = layout.vertices[place]!;
      const end = layout.vertices[after]!;
      const along = end.clone().sub(start);
      bond.position.copy(start).add(end).multiplyScalar(0.5);
      bond.quaternion.copy(this.turn.setFromUnitVectors(ALONG, along.clone().normalize()));
      bond.scale.set(BOND_RADIUS, along.length(), BOND_RADIUS);
    }

    for (let extra = size; extra < this.atoms.length; extra++) {
      this.atoms[extra]!.body.visible = false;
      this.atoms[extra]!.shell.visible = false;
    }
    for (let extra = size; extra < this.bonds.length; extra++) {
      this.bonds[extra]!.visible = false;
    }

    this.root.visible = true;
    this.stage.requestRender();
  }

  /**
   * @param through whether to draw it over everything
   */
  seeThrough(through: boolean): void {
    for (const material of [...Object.values(this.bodies), ...Object.values(this.shells)]) {
      material.depthTest = !through;
    }
    this.stage.requestRender();
  }

  /** Stops drawing the ring. */
  hide(): void {
    if (this.root.visible) {
      this.root.visible = false;
      this.stage.requestRender();
    }
  }

  dispose(): void {
    this.root.removeFromParent();
    this.sphere.dispose();
    this.shellGeometry.dispose();
    this.rod.dispose();
    [...Object.values(this.bodies), ...Object.values(this.shells)].forEach((material) => material.dispose());
  }

  /** The ghost atom for one place on the ring. */
  private atomAt(place: number): GhostAtom {
    let ghost = this.atoms[place];
    if (!ghost) {
      const body = new Mesh(this.sphere, this.bodies.later);
      const shell = new Mesh(this.shellGeometry, this.shells.later);
      body.renderOrder = 11;
      shell.renderOrder = 12;
      this.root.add(body, shell);
      ghost = { body, shell };
      this.atoms[place] = ghost;
    }
    return ghost;
  }

  /** The ghost bond for one side of the polygon. */
  private bondAt(side: number): Mesh {
    let bond = this.bonds[side];
    if (!bond) {
      bond = new Mesh(this.rod, this.bodies.later);
      bond.renderOrder = 10;
      this.root.add(bond);
      this.bonds[side] = bond;
    }
    return bond;
  }

  private glass(colour: number, opacity: number): MeshBasicMaterial {
    return new MeshBasicMaterial({ color: colour, transparent: true, opacity, depthWrite: false, side: DoubleSide });
  }

  private wire(colour: number, opacity: number): MeshBasicMaterial {
    return new MeshBasicMaterial({ color: colour, transparent: true, opacity, wireframe: true, depthWrite: false });
  }
}
