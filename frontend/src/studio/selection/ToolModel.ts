import type {Vec3} from "../../api/types/structure";
import type {Hold} from "./bondLength";
import type {Constraint} from "../viewport/dragPlane";
import {Observable} from "./observable";

/**
 * What a left-drag does: select, move atoms or the whole molecule, rotate the whole molecule, or pan the view, in which
 * case a right-drag orbits it instead.
 */
export type Tool = "select" | "move" | "rotate" | "pan";

/** Which axes a constraint, and the molecule's gizmo, is measured along: the scene's or the molecule's own. */
export type Space = "world" | "local";

/**
 * The point a molecule turns about, and where its gizmo sits: the point the view orbits (which a right-click on an atom
 * moves onto that atom), the middle of its atoms, its own origin, or a point given.
 */
export type Pivot = "anchor" | "centre" | "origin" | "point";

/** A torsion being shown: the bond it is about, and the atom at each end it is measured from. */
export interface TorsionView {
  bond: string;
  first: string;
  second: string;
}

/** The tool in use, and what a move is held to. */
export interface ToolState {
  tool: Tool;
  constraint: Constraint;
  space: Space;
  pivot: Pivot;
  /** Where the pivot is when it is `point`, in the scene, in angstroms. */
  point: Vec3;
  /** Whether strain is shown: marked in the viewport, and detailed for what is selected. */
  strain: boolean;
  /** Whether moving an atom carries the hydrogens bonded to it. This is a convenience, and nothing is locked. */
  carryHydrogens: boolean;
  /** The torsion the Inspector is showing for the selected bond, which the viewport marks, or null for none. */
  torsion: TorsionView | null;
  /** Which atom of a bond stays where it is when the bond is lengthened or turned, by the Inspector or by dragging a handle. */
  hold: Hold;
}

/** Which tool is in use. The toolbar and the viewport share it, and neither writes to the other. */
export class ToolModel extends Observable<ToolState> {
  constructor() {
    super({ tool: "select", constraint: "free", space: "world", pivot: "anchor", point: { x: 0, y: 0, z: 0 }, strain: false, carryHydrogens: false, torsion: null, hold: "first" });
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
   * Measures constraints and the molecule's gizmo along the scene's axes or the molecule's own.
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
   * Sets the point the pivot is at when it is `point`.
   *
   * @param point where, in the scene, in angstroms
   */
  setPoint(point: Vec3): void {
    this.set({ ...this.get(), point });
  }
}
