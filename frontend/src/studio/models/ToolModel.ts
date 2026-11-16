import type {Vec3} from "../../api/types/structure";
import type {Hold} from "../geometry/bondLength";
import type {Constraint} from "../viewport/handles/dragPlane";
import {Observable} from "./observable";

/**
 * What a left-drag does: select, move atoms or the whole molecule, rotate the whole molecule, or pan the view, in which
 * case a right-drag orbits it instead.
 */
export type Tool = "select" | "move" | "rotate" | "pan";

/**
 * What the rotate tool turns: the whole molecule, about a pivot, or something inside it, which is a bond that is clicked,
 * turned about to change its torsion, or an atom that is clicked, whose angle between two of its bonds is changed.
 */
export type RotateTarget = "molecule" | "internal";

/** Which axes a constraint, and the molecule's handles, are measured along: the scene's or the molecule's own. */
export type Space = "world" | "local";

/**
 * The point a molecule turns about, and where its handles sit: the point the view orbits (which a right-click on an atom
 * moves onto that atom), the middle of its atoms, its own origin, or a point given.
 */
export type Pivot = "anchor" | "centre" | "origin" | "point";

/** A torsion being shown: the bond it is about, and the atom at each end it is measured from. */
export interface TorsionView {
  bond: string;
  first: string;
  second: string;
}

/** An angle being shown: the atom it is at, and the two atoms bonded to it that it is measured between. */
export interface AngleView {
  centre: string;
  from: string;
  to: string;
}

/** The tool in use, and what a move is held to. */
export interface ToolState {
  tool: Tool;
  constraint: Constraint;
  space: Space;
  pivot: Pivot;
  /** What the rotate tool turns. */
  rotateTarget: RotateTarget;
  /** Where the pivot is when it is `point`, in the scene, in angstroms. */
  point: Vec3;
  /** The bond the rotate tool turns the whole molecule about, as an axle, or null to turn it about the pivot freely. */
  axis: string | null;
  /** Whether strain is shown: marked in the viewport, and detailed for what is selected. */
  strain: boolean;
  /** Whether moving an atom carries the hydrogens bonded to it. This is a convenience, and nothing is locked. */
  carryHydrogens: boolean;
  /** Whether a dragged atom snaps to the grid, while one is shown. */
  snap: boolean;
  /** The torsion the Inspector is showing for the selected bond, which the viewport marks, or null for none. */
  torsion: TorsionView | null;
  /** The angle the Inspector is showing for the selected atom, which the viewport marks, or null for none. */
  angle: AngleView | null;
  /** The pi systems marked in the viewport: the label of the ring the Inspector shows, and the clouds the user turned on, which stay until turned off. */
  system: { label: string | null; clouds: readonly string[] };
  /** Which atom of a bond stays where it is when the bond is lengthened or turned, by the Inspector or by dragging a handle. */
  hold: Hold;
}

/** Which tool is in use. The toolbar and the viewport share it, and neither writes to the other. */
export class ToolModel extends Observable<ToolState> {
  constructor() {
    super({ tool: "select", constraint: "free", space: "world", pivot: "anchor", rotateTarget: "molecule", point: { x: 0, y: 0, z: 0 }, axis: null, strain: false, carryHydrogens: false, snap: false, torsion: null, angle: null, system: { label: null, clouds: [] }, hold: "first" });
  }

  /**
   * Switches tool. The constraint is kept, so coming back to move finds it as it was left.
   *
   * @param tool the tool to use
   */
  use(tool: Tool): void {
    if (this.get().tool !== tool) {
      this.set({ ...this.get(), tool });
    }
  }

  /**
   * Holds moves to an axis or plane, or lets them go.
   *
   * @param constraint what a move is held to
   */
  constrain(constraint: Constraint): void {
    if (this.get().constraint !== constraint) {
      this.set({ ...this.get(), constraint });
    }
  }

  /**
   * Measures constraints and the molecule's handles along the scene's axes or the molecule's own.
   *
   * @param space which to use
   */
  useSpace(space: Space): void {
    if (this.get().space !== space) {
      this.set({ ...this.get(), space });
    }
  }

  /**
   * Chooses what the molecule turns about.
   *
   * @param pivot the pivot to use
   */
  pivotOn(pivot: Pivot): void {
    if (this.get().pivot !== pivot) {
      this.set({ ...this.get(), pivot });
    }
  }

  /**
   * Chooses the bond the rotate tool turns the whole molecule about.
   *
   * @param bond the bond's identifier, or null to turn the molecule freely about its pivot
   */
  turnAbout(bond: string | null): void {
    if (this.get().axis !== bond) {
      this.set({ ...this.get(), axis: bond });
    }
  }

  /**
   * Chooses what the rotate tool turns.
   *
   * @param target the whole molecule, or something inside it
   */
  rotateTarget(target: RotateTarget): void {
    if (this.get().rotateTarget !== target) {
      this.set({ ...this.get(), rotateTarget: target });
    }
  }

  /**
   * Shows strain, or stops showing it.
   *
   * @param on whether to show it
   */
  showStrain(on: boolean): void {
    if (this.get().strain !== on) {
      this.set({ ...this.get(), strain: on });
    }
  }

  /**
   * Makes moving an atom carry the hydrogens bonded to it, or stops it.
   *
   * @param on whether to carry them
   */
  carry(on: boolean): void {
    if (this.get().carryHydrogens !== on) {
      this.set({ ...this.get(), carryHydrogens: on });
    }
  }

  /**
   * Makes a dragged atom snap to the grid, or stops it.
   *
   * @param on whether to snap
   */
  snapToGrid(on: boolean): void {
    if (this.get().snap !== on) {
      this.set({ ...this.get(), snap: on });
    }
  }

  /**
   * Chooses which atom of a bond stays where it is when the bond is lengthened or turned.
   *
   * @param hold the first atom, the second, or neither
   */
  holding(hold: Hold): void {
    if (this.get().hold !== hold) {
      this.set({ ...this.get(), hold });
    }
  }

  /**
   * Says which torsion the viewport should mark.
   *
   * @param torsion the bond and the atoms it is measured between, or null for none
   */
  showTorsion(torsion: TorsionView | null): void {
    const was = this.get().torsion;
    if (was?.bond !== torsion?.bond || was?.first !== torsion?.first || was?.second !== torsion?.second) {
      this.set({ ...this.get(), torsion });
    }
  }

  /**
   * Says which angle the viewport should mark.
   *
   * @param angle the atom and the two atoms it is measured between, or null for none
   */
  showAngle(angle: AngleView | null): void {
    const was = this.get().angle;
    if (was?.centre !== angle?.centre || was?.from !== angle?.from || was?.to !== angle?.to) {
      this.set({ ...this.get(), angle });
    }
  }

  /**
   * Says whose pi system the viewport labels, which is the ring the Inspector shows.
   *
   * @param ring the identifier of a ring in the system, or null for none
   */
  showSystem(ring: string | null): void {
    const { system } = this.get();
    if (system.label !== ring) {
      this.set({ ...this.get(), system: { ...system, label: ring } });
    }
  }

  /**
   * Turns the cloud of a ring's pi system on or off. It stays drawn whatever is selected.
   *
   * @param ring the identifier of a ring in the system
   * @param on whether the cloud is drawn
   */
  showCloud(ring: string, on: boolean): void {
    const { system } = this.get();
    if (system.clouds.includes(ring) !== on) {
      const clouds = on ? [...system.clouds, ring] : system.clouds.filter((each) => each !== ring);
      this.set({ ...this.get(), system: { ...system, clouds } });
    }
  }

  /**
   * Sets the point the pivot is at when it is `point`.
   *
   * @param point where, in the scene, in angstroms
   */
  setPoint(point: Vec3): void {
    this.set({ ...this.get(), point });
  }
}
