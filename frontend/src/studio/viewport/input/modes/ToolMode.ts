import type {Vec3} from "../../../../api/types/structure";
import type {Stage} from "../../../../render/stage";
import type {Binding} from "../../../keys/binding";
import type {Hold} from "../../../geometry/bondLength";
import type {AtomPlacement, BondOrder} from "../../../session/messages";
import type {Item} from "../../../models/items";
import type {StateIndex} from "../../../geometry/StateIndex";
import type {AngleView, EditClick, RotateTarget, Space, Tool, TorsionView} from "../../../models/ToolModel";
import type {CameraRig} from "../../camera/CameraRig";
import type {CameraLock} from "../../camera/CameraLock";
import type {Constraint} from "../../handles/dragPlane";
import type {BondHandleController} from "../../handles/BondHandleController";
import type {MoleculeHandleController} from "../../handles/MoleculeHandleController";
import type {CameraHud} from "../../hud/CameraHud";
import type {Annotations} from "../../scene/Annotations";
import type {MoleculeScene} from "../../scene/MoleculeScene";
import type {PanGuide} from "../../scene/PanGuide";
import type {ReferenceLayer} from "../../scene/ReferenceLayer";
import type {RingGuide} from "../../scene/RingGuide";
import type {StudioViewListener} from "../../StudioViewListener";
import type {InteractionHandler} from "../PointerRouter";

export interface ModeOptions {
  /** What a dragged atom is held to. */
  constraint: Constraint;
  /** Whether moving an atom carries the hydrogens bonded to it. */
  carryHydrogens: boolean;
  /** Whether a dragged atom snaps to the grid, while one is shown. */
  snap: boolean;
  /** Whether the rotate tool turns the whole molecule, or what is inside it. */
  rotateTarget: RotateTarget;
  /** What a click does with the edit tool. */
  editClick: EditClick;
  /** How many atoms the edit tool's ring has. */
  ringSize: number;
  /** The order of the bonds the edit tool makes. */
  bondOrder: BondOrder;
  /** What is selected. */
  selection: readonly Item[];
  /** What the session's structure holds, or null before it is known. */
  index: StateIndex | null;
  /** Which atom a bond's or an angle's handle keeps where it is. */
  hold: Hold;
  /** The torsion the Inspector is showing, whose atoms the turn handle measures from. */
  torsion: TorsionView | null;
  /** The angle the Inspector is showing, whose bonds the angle handle opens and closes. */
  angle: AngleView | null;
}

/** What a tool's mode uses of the view around it. */
export interface ModeHost {
  readonly stage: Stage;
  readonly scene: MoleculeScene;
  readonly rig: CameraRig;
  /** Keeps the camera's own controls off while something is held. */
  readonly lock: CameraLock;
  readonly reference: ReferenceLayer;
  readonly annotations: Annotations;
  readonly cameraHud: CameraHud;
  /** What debugging shows while the view is panned. */
  readonly panGuide: PanGuide;
  /** The handles on the molecule as a whole. */
  readonly molecule: MoleculeHandleController;
  /** The handle on a bond or an angle. */
  readonly bonds: BondHandleController;
  /** Whether constraints, and the molecule's handles, follow the scene's axes or the molecule's. */
  space(): Space;
  /** Moves atoms, redrawing them and what is drawn about them. */
  moveAtoms(moves: Iterable<readonly [string, Vec3]>): void;
  /** Draws, or stops drawing, the selection and hover as plain tints without lengths or names. */
  setPlain(plain: boolean): void;
  /** Labels, or stops labelling, the lengths and names of what is selected or hovered. The tints are unchanged. */
  setMeasured(measured: boolean): void;
  /** Tells every listener something. */
  emit(tell: (listener: StudioViewListener) => void): void;
  /** Adds an atom, which answers with its name or null if none was made. */
  place(placement: AtomPlacement): Promise<string | null>;
  /** Bonds two atoms. */
  join(first: string, second: string): Promise<void>;
  /** The ring the edit tool draws, which grows an atom into a ring. */
  readonly ringGuide: RingGuide;
}

/** How the viewport answers the pointer and the keyboard while one {@link Tool} is chosen. */
export interface ToolMode extends InteractionHandler {
  readonly tool: Tool;
  /** Takes up the mode: sets its cursor and camera button mapping. */
  enter(): void;
  /** Puts the mode down, abandoning anything it had in hand and taking away its handles. */
  exit(): void;
  /** Takes the tool's settings and the selection, and shows the handles they call for. */
  configure(options: ModeOptions): void;
  /** The molecule drawn was replaced by another, so nothing held of the old one is held any more. */
  reset(): void;
  /** The shortcuts the mode adds while it is in use. */
  keys(): Binding[];
  /** Whether the mode has an atom in hand that the pointer is carrying. */
  readonly dragging: boolean;
  /** The atoms the mode is carrying, which are where it has put them and not where the session last said. */
  held(): string[];
  /** Abandons a drag in progress, putting the atoms back where they were picked up. */
  cancelDrag(): void;
  /** Puts away what the mode is showing that is not a drag, which Escape does. Returns whether there was anything to put away. */
  dismiss?(): boolean;
  /** The cursor over the viewport when nothing is under the pointer. */
  resting(): string;
}
