import type {AtomMove, AtomPlacement, BondEdit, Pose} from "../session/messages";
import type {CameraState} from "../models/CameraModel";
import type {PartItem} from "../models/items";
import type {Constraint} from "./handles/dragPlane";

/** Where the pointer is, in pixels. */
export interface ScreenPoint {
  x: number;
  y: number;
}

/** A rectangle on the screen, in pixels from the canvas's top left corner. */
export interface ScreenRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface StudioViewListener {
  /** The pointer moved over `pick`, or over nothing if it is null. Not called while a button is held. */
  hover?(pick: PartItem | null, at: ScreenPoint): void;
  /** A left click that was not a camera drag. `additive` is whether shift, control or command was held. */
  click?(pick: PartItem | null, additive: boolean): void;
  /**
   * The edit tool wants an atom added: bonded to the atom clicked, free where the empty space clicked is, or at a place
   * the tool has chosen. Answers with the name of the atom made, or null if none was.
   */
  place?(placement: AtomPlacement): Promise<string | null>;
  /** The edit tool wants two atoms bonded, with the order it is set to. */
  bond?(first: string, second: string): Promise<void>;
  /** The edit tool's ring has been given another size, by the scroll wheel. */
  ringSize?(size: number): void;
  /** A rectangle is being dragged to select the atoms inside it, and is at `rect`, or has gone if it is null. */
  updateSelectionArea?(rect: ScreenRect | null): void;
  /** The selection rectangle was released with `atoms` inside it. */
  releaseSelectionArea?(atoms: string[]): void;
  /** An atom has been picked up by the move tool and has begun to move, along with the rest of the selection if it is in it. */
  dragStart?(id: string): void;
  /** The dragged atoms are at `moves`' positions. `done` is true for the last report, when the pointer is released. */
  drag?(moves: AtomMove[], done: boolean): void;
  /** A drag was abandoned, and the atoms put back at `moves`' positions, where they were picked up. */
  dragCancel?(moves: AtomMove[]): void;
  /** The move tool wants a drag held to `constraint`: a handle that translates was taken hold of, or its key was pressed. */
  constraint?(constraint: Constraint): void;
  /** The move tool wants moving atoms to carry their hydrogens, or not: its key was pressed. */
  carryHydrogens?(carry: boolean): void;
  /** A handle of the molecule's handles has been taken hold of, with the molecule at `from`. */
  moleculeDragStart?(from: Pose): void;
  /** The molecule handles have put the molecule at `pose`. `done` is true for the last report, when the handle is let go. */
  moleculeDrag?(pose: Pose, done: boolean): void;
  /** A drag of the molecule was abandoned, and it was put back at `from`, where it was picked up. */
  moleculeDragCancel?(from: Pose): void;
  /** A bond's handle has put the bond at `edit`. `done` is true for the last report, when the handle is let go. */
  bondDrag?(edit: BondEdit, done: boolean): void;
  /** A drag of a bond's handle was abandoned, and the session is to put the bond back. */
  bondDragCancel?(): void;
  /** The camera's state changed. `byUser` - whether a user change or initial load from server. */
  camera?(camera: CameraState, byUser: boolean): void;
}
