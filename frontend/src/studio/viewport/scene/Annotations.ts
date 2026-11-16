import {Color, DoubleSide, Group, Mesh, MeshBasicMaterial, PlaneGeometry, Vector3} from "three";
import {ballRadius} from "../../../render/bonds";
import {disposeMaterials} from "../../../render/dispose";
import type {Primitives} from "../../../render/primitives";
import type {Stage} from "../../../render/stage";
import type {GeometryResult} from "../../session/messages";
import type {AngleView, TorsionView} from "../../selection/ToolModel";
import {torsion} from "../../selection/torsion";
import {angleAt, planeNormal} from "../../selection/angle";
import {type AtomNode, type MoleculeScene, STYLE} from "./MoleculeScene";
import {NO_ITEMS} from "../../selection/items";

/** How many selected atoms there can be for the bonds on them to be labelled with their lengths. */
const LENGTH_LABEL_ATOMS = 4;

/** The most bonds that are labelled with their lengths at once. */
const LENGTH_LABEL_BONDS = 12;

/** The colour of what marks strain. */
const STRAIN_COLOUR = "#ff2a2a";

/** The colour of the halo on an atom that is only a little strained, which turns to {@link STRAIN_COLOUR} as it gets worse. */
const MILD_STRAIN_COLOUR = new Color("#ffc233");

/** How strained, in tolerances, an atom has to be for its halo to be as strong as it gets. */
const STRAIN_SATURATED = 6;

/** The colour of the bonds that make a strained angle. */
const ANGLE_COLOUR = "#ff9a1f";

export class Annotations {
  debug = false;

  /** What the session last said of its geometry, which the strain markers are drawn from. */
  private strainResult: GeometryResult | null = null;
  /** The strain markers, which sit in the molecule's group so that they move with it, or null while there are none. */
  private strainMarks: Group | null = null;
  private lengthMarks: Group | null = null;
  private torsionShown: TorsionView | null = null;
  private angleShown: AngleView | null = null;

  constructor(
    private readonly stage: Stage,
    private readonly kit: Primitives,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the marks, which were taken away along with everything else drawn in the molecule's group. */
  forget(): void {
    this.strainMarks = null;
    this.lengthMarks = null;
  }

  /**
   * Marks where the geometry is strained.
   *
   * @param result what the session said of its geometry, or null to show nothing
   */
  setStrain(result: GeometryResult | null): void {
    this.strainResult = result;
    this.drawStrain();
  }

  /**
   * Marks a torsion: the two bonds to the atoms it is measured from, their names, and its angle at the middle of the
   * bond it is about.
   *
   * @param torsion the torsion to mark, or null for none
   */
  setTorsion(torsion: TorsionView | null): void {
    this.torsionShown = torsion;
    this.drawLengths();
  }

  /**
   * Marks an angle: the two bonds, the names of the atoms at their ends, and its size inside it. With debugging on it also
   * draws the plane the angle opens and closes in.
   *
   * @param angle the angle to mark, or null for none
   */
  setAngle(angle: AngleView | null): void {
    this.angleShown = angle;
    this.drawLengths();
  }

  /**
   * Labels the length of each bond that is selected, and of each bond on a few selected atoms
   */
  drawLengths(): void {
    const { content, atomById, bonds } = this.scene;
    if (this.lengthMarks) {
      content.remove(this.lengthMarks);
      disposeMaterials(this.lengthMarks);
      this.lengthMarks = null;
    }

    const { selected, hover } = this.scene.plain ? { selected: NO_ITEMS, hover: NO_ITEMS } : this.scene.emphasis;
    const few = selected.atoms.size <= LENGTH_LABEL_ATOMS;
    const measured = bonds.filter(({ link }) => selected.bonds.has(link.bond.id) || (few && (selected.atoms.has(link.bond.first) || selected.atoms.has(link.bond.second))));
    const named = bonds.filter(({ link }) => selected.bonds.has(link.bond.id) || hover.bonds.has(link.bond.id));

    const marks = new Group();
    if (measured.length <= LENGTH_LABEL_BONDS) {
      for (const { link } of measured) {
        const first = atomById.get(link.bond.first);
        const second = atomById.get(link.bond.second);
        if (first && second) {
          const note = this.kit.labels.label(`${first.at.distanceTo(second.at).toFixed(3)} Å`, "#1f3d99", 0.22);
          note.position.copy(first.at).add(second.at).multiplyScalar(0.5);
          marks.add(note);
        }
      }
    }

    // each atom is named once however many of the bonds on it are named
    const atomsNamed = new Set(named.flatMap(({ link }) => [link.bond.first, link.bond.second]));
    for (const id of atomsNamed) {
      const node = atomById.get(id);
      if (node) {
        // the name sits just above the atom, which is as far as the ball reaches plus a little
        const note = this.kit.labels.label(`${node.atom.element}${node.index + 1}`, "#222222", 0.26);
        note.position.copy(node.at);
        note.position.y += ballRadius(node.atom.radius, STYLE, true) + 0.25;
        marks.add(note);
      }
    }

    this.markTorsion(marks);
    this.markAngle(marks);

    if (marks.children.length > 0) {
      content.add(marks);
      this.lengthMarks = marks;
    }
    this.stage.requestRender();
  }

  /**
   * Adds the marks of the torsion being shown, if its atoms are all drawn.
   *
   * @param marks the group to add them to
   */
  private markTorsion(marks: Group): void {
    const shown = this.torsionShown;
    const bond = shown && this.scene.bonds.find(({ link }) => link.bond.id === shown.bond)?.link.bond;
    const [a, b, c, d] = shown && bond ? [shown.first, bond.first, bond.second, shown.second].map((id) => this.scene.atomById.get(id)) : [];
    if (!a || !b || !c || !d) {
      return;
    }

    const line = new MeshBasicMaterial({ color: ANGLE_COLOUR, depthTest: false });
    marks.add(this.kit.cylinder(a.at, b.at, 0.035, line));
    marks.add(this.kit.cylinder(c.at, d.at, 0.035, line.clone()));

    for (const node of [a, d]) {
      const name = this.kit.labels.label(`${node.atom.element}${node.index + 1}`, "#222222", 0.26);
      name.position.copy(node.at);
      name.position.y += ballRadius(node.atom.radius, STYLE, true) + 0.25;
      marks.add(name);
    }

    const angle = torsion(a.at, b.at, c.at, d.at);
    const note = this.kit.labels.label(`${angle.toFixed(1)}°`, "#b35900", 0.24);
    note.position.copy(b.at).add(c.at).multiplyScalar(0.5);
    note.position.y -= 0.3;
    marks.add(note);
  }

  /**
   * Adds the marks of the angle being shown, if its atoms are all drawn.
   *
   * @param marks the group to add them to
   */
  private markAngle(marks: Group): void {
    const shown = this.angleShown;
    const centre = shown && this.scene.atomById.get(shown.centre);
    const from = shown && this.scene.atomById.get(shown.from);
    const to = shown && this.scene.atomById.get(shown.to);
    if (!centre || !from || !to) {
      return;
    }

    const line = new MeshBasicMaterial({ color: ANGLE_COLOUR, depthTest: false });
    marks.add(this.kit.cylinder(centre.at, from.at, 0.035, line));
    marks.add(this.kit.cylinder(centre.at, to.at, 0.035, line.clone()));

    for (const node of [from, to]) {
      const name = this.kit.labels.label(`${node.atom.element}${node.index + 1}`, "#222222", 0.26);
      name.position.copy(node.at);
      name.position.y += ballRadius(node.atom.radius, STYLE, true) + 0.25;
      marks.add(name);
    }

    // the size is written inside the angle, on the line that bisects it
    const inside = from.at.clone().sub(centre.at).normalize().add(to.at.clone().sub(centre.at).normalize());
    const note = this.kit.labels.label(`${angleAt(from.at, centre.at, to.at).toFixed(1)}°`, "#b35900", 0.24);
    note.position.copy(centre.at).addScaledVector(inside.lengthSq() > 1e-9 ? inside.normalize() : new Vector3(0, 1, 0), 0.6);
    marks.add(note);

    if (this.debug) {
      marks.add(this.anglePlane(centre.at, from.at, to.at));
    }
  }

  /**
   * Makes the plane an angle opens and closes in.
   *
   * @param centre where the angle is
   * @param from the end of one bond
   * @param to the end of the other
   * @returns the sheet, in the molecule's own frame
   */
  private anglePlane(centre: Vector3, from: Vector3, to: Vector3): Mesh {
    const normal = planeNormal(from.clone().sub(centre), to.clone().sub(centre));
    const size = 2 * Math.max(from.distanceTo(centre), to.distanceTo(centre)) + 1;
    const sheet = new Mesh(
      new PlaneGeometry(size, size),
      new MeshBasicMaterial({ color: "#7a3fff", transparent: true, opacity: 0.16, side: DoubleSide, depthWrite: false }),
    );
    sheet.position.copy(centre);
    // a plane is made facing +z, which is turned to face along the normal
    sheet.quaternion.setFromUnitVectors(new Vector3(0, 0, 1), new Vector3(normal.x, normal.y, normal.z));
    sheet.renderOrder = 4;
    return sheet;
  }

  /** Draws the strain markers. */
  private drawStrain(): void {
    const { content, atomById, atoms } = this.scene;
    if (this.strainMarks) {
      content.remove(this.strainMarks);
      disposeMaterials(this.strainMarks);
      this.strainMarks = null;
    }

    const result = this.strainResult;
    if (result && result.status === "complete" && atoms.length > 0) {
      const marks = new Group();
      for (const { id, strain } of result.atom_strain) {
        const node = atomById.get(id);
        if (!node) {
          continue;
        }
        // a share is in tolerances, and six is as strained as it is worth telling apart: a faint amber halo that grows,
        // brightens and turns solid red as it gets there
        const amount = Math.min(1, strain / STRAIN_SATURATED);
        const halo = new Mesh(
          this.kit.sphereGeometry,
          new MeshBasicMaterial({
            color: MILD_STRAIN_COLOUR.clone().lerp(new Color(STRAIN_COLOUR), amount),
            transparent: true,
            opacity: 0.25 + 0.65 * amount,
            depthWrite: false,
          }),
        );
        halo.position.copy(node.at);
        halo.scale.setScalar(ballRadius(node.atom.radius, STYLE, true) * (1.25 + 0.4 * amount));
        halo.renderOrder = 5;
        marks.add(halo);
      }
      for (const clash of result.clashes) {
        const first = atomById.get(clash.first);
        const second = atomById.get(clash.second);
        if (first && second) {
          marks.add(this.kit.cylinder(first.at, second.at, 0.03, new MeshBasicMaterial({ color: STRAIN_COLOUR, depthTest: false })));
        }
      }

      const line = (a: Vector3, b: Vector3, colour: string, width: number) =>
        marks.add(this.kit.cylinder(a, b, width, new MeshBasicMaterial({ color: colour, depthTest: false })));
      for (const finding of result.findings) {
        const nodes = finding.atoms.map((id) => atomById.get(id));
        if (nodes.some((node) => !node)) {
          continue;
        }
        const [first, middle, last] = nodes as AtomNode[];

        if (finding.kind === "bond" && first && middle) {
          // the bond itself, with how long it is
          line(first.at, middle.at, STRAIN_COLOUR, 0.05);
          const note = this.kit.labels.label(`${finding.actual.toFixed(3)} Å`, "#c00000", 0.22);
          note.position.copy(first.at.clone().add(middle.at).multiplyScalar(0.5));
          marks.add(note);
        } else if (finding.kind === "angle" && first && middle && last) {
          // the two bonds that make the angle, with how wide it is at the atom between them
          line(middle.at, first.at, ANGLE_COLOUR, 0.035);
          line(middle.at, last.at, ANGLE_COLOUR, 0.035);
          const inside = first.at.clone().sub(middle.at).normalize().add(last.at.clone().sub(middle.at).normalize());
          const note = this.kit.labels.label(`${finding.actual.toFixed(1)}°`, "#b35900", 0.22);
          note.position.copy(middle.at).addScaledVector(inside.lengthSq() > 1e-9 ? inside.normalize() : new Vector3(0, 1, 0), 0.6);
          marks.add(note);
        }
      }
      content.add(marks);
      this.strainMarks = marks;
    }
    this.stage.requestRender();
  }
}
