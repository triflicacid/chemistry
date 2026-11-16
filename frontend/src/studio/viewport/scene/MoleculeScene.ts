import {Box3, Color, Group, Mesh, MeshStandardMaterial, type Object3D, Ray, Raycaster, type Sprite, Vector3} from "three";
import type {Vec3} from "../../../api/types/structure";
import {aromaticCentres} from "../../../render/aromatic";
import {ballRadius, type BondEnds, buildBond, type Style} from "../../../render/bonds";
import {disposeMaterials} from "../../../render/dispose";
import {ACTIVE_TINT, applyTints, HOVER_TINT, SELECTED_TINT} from "../../../render/emphasis";
import {type Part, PartRegistry} from "../../../render/parts";
import type {Primitives} from "../../../render/primitives";
import type {Stage} from "../../../render/stage";
import type {SessionAtom, SessionBond, SessionState} from "../../session/messages";
import type {ItemSet, PartItem} from "../../selection/items";
import {type Emphasis, NO_ITEMS} from "../../selection/items";


/** How the molecule is drawn. */
export const STYLE: Style = "ball_and_stick";

/** A bond as the drawing holds it: which atoms it joins by position in the atom list, so the shared drawing code can take it. */
export interface Link extends BondEnds {
  bond: SessionBond;
}

export interface AtomNode {
  atom: SessionAtom;
  index: number;
  /** Where the atom is. The ball and the charge badge both read this, so moving it moves them. */
  at: Vector3;
  ball: Mesh;
  badge: Sprite | null;
  /** The indices of the atoms bonded to this one. */
  neighbours: number[];
}

export interface BondNode {
  link: Link;
  drawn: { group: Group; part: Part } | null;
}

/** Everything about a state that decides how it is drawn, other than the atoms' positions. */
export function structureKeyOf(state: SessionState): string {
  const atoms = state.atoms.map((atom) => `${atom.id}:${atom.element}:${atom.charge}:${atom.colour}:${atom.radius}`);
  const bonds = state.bonds.map((bond) => `${bond.id}:${bond.first}:${bond.second}:${bond.order}`);
  return `${atoms.join(",")}|${bonds.join(",")}`;
}

export class MoleculeScene {
  readonly content = new Group();
  atoms: AtomNode[] = [];
  bonds: BondNode[] = [];
  atomById = new Map<string, AtomNode>();
  /** What is tinted. It is kept across redraws as atoms move. */
  emphasis: Emphasis = { selected: NO_ITEMS, active: NO_ITEMS, hover: NO_ITEMS };
  /** Whether to leave what is selected undrawn, with no lengths or names for it or for what is hovered. Hover is still tinted. */
  plain = false;
  locked: ReadonlySet<string> = new Set();
  /** A fingerprint of everything about the structure except where its atoms are. */
  structureKey = "";

  private readonly registry = new PartRegistry();
  private readonly raycaster = new Raycaster();
  /** The padlock drawn on each locked atom. */
  private lockBadges = new Map<string, Sprite>();
  private bondsOf = new Map<number, BondNode[]>();
  private ringCentres = new Map<Link, Vector3>();
  /** The atoms bonded to each atom, by position in the atom list, as the drawing code wants them. */
  private neighbourMap = new Map<number, number[]>();

  constructor(
    private readonly stage: Stage,
    private readonly kit: Primitives,
  ) {}

  /** Where an atom is, by position in the atom list. A copy, since the drawing code moves the points it is given. */
  private readonly point = (index: number): Vector3 => (this.atoms[index]?.at ?? new Vector3()).clone();

  /** Draws a state afresh. What was drawn must have been cleared. */
  build(state: SessionState): void {
    const index = new Map(state.atoms.map((atom, i) => [atom.id, i]));
    this.atoms = state.atoms.map((atom, i) => this.drawAtom(atom, i));
    this.atomById = new Map(this.atoms.map((node) => [node.atom.id, node]));

    const links: Link[] = [];
    for (const bond of state.bonds) {
      const first = index.get(bond.first);
      const second = index.get(bond.second);
      if (first !== undefined && second !== undefined) {
        links.push({ bond, first, second, order: bond.order });
      }
    }
    for (const link of links) {
      this.atoms[link.first]?.neighbours.push(link.second);
      this.atoms[link.second]?.neighbours.push(link.first);
    }

    this.neighbourMap = new Map(this.atoms.map((node) => [node.index, node.neighbours]));
    this.ringCentres = aromaticCentres(links, this.point);
    this.bonds = links.map((link) => ({ link, drawn: null }));
    this.bondsOf = new Map();
    for (const bond of this.bonds) {
      for (const end of [bond.link.first, bond.link.second]) {
        this.bondsOf.set(end, [...(this.bondsOf.get(end) ?? []), bond]);
      }
      this.redraw(bond);
    }
    this.refreshEmphasis();
  }

  private drawAtom(atom: SessionAtom, index: number): AtomNode {
    const at = new Vector3(atom.position.x, atom.position.y, atom.position.z);
    const radius = ballRadius(atom.radius, STYLE, true);
    const ball = new Mesh(this.kit.sphereGeometry, new MeshStandardMaterial({ color: new Color(atom.colour), roughness: 0.5 }));
    ball.position.copy(at);
    ball.scale.setScalar(radius);
    this.registry.add("atom", null, { element: atom.element }, ball, atom.id);
    this.content.add(ball);

    let badge: Sprite | null = null;
    if (atom.charge !== 0) {
      badge = this.kit.labels.chargeBadge(this.content, atom.charge, at, radius);
      this.content.add(badge);
    }
    return { atom, index, at, ball, badge, neighbours: [] };
  }

  /** Draws a bond afresh from where its atoms are now, replacing what was drawn. */
  private redraw(bond: BondNode): void {
    if (bond.drawn) {
      this.content.remove(bond.drawn.group);
      disposeMaterials(bond.drawn.group);
      this.registry.remove(bond.drawn.part);
      bond.drawn = null;
    }

    const group = buildBond(
      this.kit,
      bond.link,
      this.atoms.map((node) => node.atom),
      STYLE,
      this.point,
      this.neighbourMap,
      this.ringCentres.get(bond.link),
    );
    if (!group) {
      return;
    }
    const part = this.registry.add("bond", null, { order: bond.link.order.toLowerCase() }, group, bond.link.bond.id);
    this.registry.claimChildren(part);
    this.content.add(group);
    bond.drawn = { group, part };
  }

  /** Takes everything drawn away. */
  clear(): void {
    disposeMaterials(this.content);
    this.content.clear();
    this.registry.clear();
    this.atoms = [];
    this.bonds = [];
    this.lockBadges = new Map();
    this.atomById = new Map();
    this.bondsOf = new Map();
    this.ringCentres = new Map();
    this.neighbourMap = new Map();
  }

  /**
   * Moves atoms, redrawing them and only the bonds their positions affect.
   *
   * @param moves each atom's identifier and where it now is; unknown identifiers and atoms already there are skipped
   * @returns whether any atom moved
   */
  moveAtoms(moves: Iterable<readonly [string, Vec3]>): boolean {
    const moved = new Set<AtomNode>();
    for (const [id, position] of moves) {
      const node = this.atomById.get(id);
      if (node && (node.at.x !== position.x || node.at.y !== position.y || node.at.z !== position.z)) {
        node.at.set(position.x, position.y, position.z);
        node.ball.position.copy(node.at);
        moved.add(node);
      }
    }
    if (moved.size === 0) {
      return false;
    }

    // a bond's extra lines fan out towards its neighbours' atoms, so the bonds one step out turn as well as the ones at the atom
    const affected = new Set<BondNode>();
    for (const node of moved) {
      for (const index of [node.index, ...node.neighbours]) {
        this.bondsOf.get(index)?.forEach((bond) => affected.add(bond));
      }
    }
    if ([...affected].some((bond) => bond.link.order === "AROMATIC")) {
      // a ring's centre is the mean of all of it, so every aromatic bond may have shifted
      this.ringCentres = aromaticCentres(this.bonds.map((bond) => bond.link), this.point);
      this.bonds.filter((bond) => bond.link.order === "AROMATIC").forEach((bond) => affected.add(bond));
    }
    affected.forEach((bond) => this.redraw(bond));
    this.refreshEmphasis();
    return true;
  }

  /** Draws a padlock on each locked atom and takes it off the rest. */
  refreshLocks(): void {
    for (const [id, badge] of this.lockBadges) {
      if (!this.locked.has(id) || !this.atomById.has(id)) {
        this.content.remove(badge);
        badge.material.dispose();
        this.lockBadges.delete(id);
      }
    }

    for (const id of this.locked) {
      const node = this.atomById.get(id);
      if (node && !this.lockBadges.has(id)) {
        const badge = this.kit.labels.lockBadge(this.content, node.at, ballRadius(node.atom.radius, STYLE, true));
        this.content.add(badge);
        this.lockBadges.set(id, badge);
      }
    }
  }

  /** Tints what is selected, active and hovered. Hover shows over active, and active over selected. */
  refreshEmphasis(): void {
    const { selected, active, hover } = this.emphasis;
    const tints = new Map<Object3D, Color>();
    for (const part of this.registry.parts) {
      const id = part.ref.id;
      if (id === undefined) {
        continue;
      }
      const among = (set: ItemSet) => (part.ref.kind === "atom" ? set.atoms : set.bonds).has(id);
      const tint = among(hover) ? HOVER_TINT : this.plain ? null : among(active) ? ACTIVE_TINT : among(selected) ? SELECTED_TINT : null;
      if (tint) {
        tints.set(part.object, tint);
      }
    }
    applyTints(this.registry.parts, tints);
    this.stage.requestRender();
  }

  /** Where an atom is in the scene, which is not where it is in the molecule's own frame once the molecule has moved. */
  worldOf(node: AtomNode): Vector3 {
    return this.content.localToWorld(node.at.clone());
  }

  /**
   * Turns a ray in the scene into the same ray in the molecule's own frame, which is where atoms are placed.
   *
   * @param ray a ray in the scene
   * @returns the ray in the molecule's frame
   */
  localRay(ray: Ray): Ray {
    const inverse = this.content.quaternion.clone().invert();
    return new Ray(this.content.worldToLocal(ray.origin.clone()), ray.direction.clone().applyQuaternion(inverse));
  }

  /** The point where the pointer's ray first meets a drawn part, in the scene, or undefined if it meets none. */
  hitPoint(event: PointerEvent): Vector3 | undefined {
    return this.registry.cast(this.raycaster, this.stage.camera, this.stage.pointer(event))?.point;
  }

  /** The part of the molecule under the pointer, or null for none. */
  pickAt(event: PointerEvent): PartItem | null {
    const hit = this.registry.cast(this.raycaster, this.stage.camera, this.stage.pointer(event));
    const id = hit?.part.ref.id;
    return hit && id !== undefined ? { kind: hit.part.ref.kind, id } : null;
  }

  /** The middle of the box round every atom, in the scene, or its origin if none is drawn. */
  middleOfAtoms(): Vector3 {
    const box = new Box3();
    this.atoms.forEach((node) => box.expandByPoint(this.worldOf(node)));
    return box.isEmpty() ? new Vector3() : box.getCenter(new Vector3());
  }
}
