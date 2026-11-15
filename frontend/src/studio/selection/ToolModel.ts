import type {Vec3} from "../../api/types/structure";
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
}

/** Which tool is in use. The toolbar and the viewport share it, and neither writes to the other. */
export class ToolModel extends Observable<ToolState> {
  constructor() {
    super({ tool: "select", constraint: "free", space: "world", pivot: "anchor", point: { x: 0, y: 0, z: 0 }, strain: false, carryHydrogens: false });
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
   * Sets the point the pivot is at when it is `point`.
   *
   * @param point where, in the scene, in angstroms
   */
  setPoint(point: Vec3): void {
    this.set({ ...this.get(), point });
  }
}
