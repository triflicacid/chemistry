import {Ray, Raycaster, Vector3} from "three";
import type {Binding} from "../../../keys/binding";
import {KEYS} from "../../../keys/keymap";
import type {AtomMove} from "../../../session/messages";
import type {Tool} from "../../../models/ToolModel";
import {
  type Basis,
  type Constraint,
  constraintForKey,
  dragged,
  pointOnConstraint,
  STANDARD_BASIS
} from "../../handles/dragPlane";
import {withTerminalHydrogens} from "../../hydrogens";
import {selectsEveryMolecule} from "../../../models/items";
import type {AtomNode} from "../../scene/MoleculeScene";
import type {ScreenPoint} from "../../StudioViewListener";
import {toVec3} from "../../vec";
import {CLICK_SLOP} from "../constants";
import {lengthTarget} from "./measurementTarget";
import {SelectMode} from "./SelectMode";
import type {ModeHost, ModeOptions} from "./ToolMode";

/** An atom that moves with a drag. */
interface Member {
  node: AtomNode;
  /** Where the atom was when it was pressed. */
  original: Vector3;
  /** Where the atom was when the current constraint began. */
  start: Vector3;
}

/** An atom the pointer is pressed on while the move tool is in use. */
interface Grab {
  /** The atom the pointer is on. */
  id: string;
  /** The atoms that move, with the one the pointer is on first. */
  members: Member[];
  /** Where the pointer's ray met the constraint when it began. */
  point: Vector3;
  /** The way the camera looks, in the molecule's own frame. */
  viewDirection: Vector3;
  /** Where the pointer was pressed. */
  from: ScreenPoint;
  /** The pointer's latest ray, in the molecule's own frame. */
  ray: Ray;
  dragging: boolean;
}

/** Moving atom(s). */
export class MoveMode extends SelectMode {
  private readonly raycaster = new Raycaster();
  private active = false;
  /** What a dragged atom is held to. */
  constraint: Constraint = "free";
  /** Whether moving an atom carries the hydrogens bonded to it. */
  private carryHydrogens = false;
  /** Whether a dragged atom snaps to the grid, which holding Ctrl reverses. */
  private snap = false;

  /** Whether snapping to the grid is switched on, whether or not there is a grid to snap to. */
  get snapOn(): boolean {
    return this.snap;
  }
  private grab: Grab | null = null;
  /** What the handles on the molecule last did. */
  private handleMode: "translate" | "atoms" | null = null;

  /**
   * @param host the view the mode is for
   * @param tool the tool the mode is for, which a mode built on this one names as its own
   */
  constructor(host: ModeHost, tool: Tool = "move") {
    super(host, tool);
  }

  override get dragging(): boolean {
    return (this.grab?.dragging ?? false) || this.boxing;
  }

  override enter(): void {
    this.active = true;
    this.host.reference.refreshGuide();
  }

  override exit(): void {
    this.active = false;
    this.cancelDrag();
    this.abandonSelectionBox();
    this.host.stage.canvas.style.cursor = "";
    this.host.reference.refreshGuide();
    this.handleMode = null;
    this.host.molecule.setMode(null);
    this.host.bonds.set(null);
    this.host.setMeasured(true);
  }

  override configure(options: ModeOptions): void {
    const { reference, molecule } = this.host;
    this.host.setMeasured(false);

    this.adoptConstraint(options.constraint);
    this.snap = options.snap;

    if (options.carryHydrogens !== this.carryHydrogens) {
      this.carryHydrogens = options.carryHydrogens;
      reference.refreshGuide();
      molecule.sync();
    }

    this.arrange(options);
    reference.refreshGuide();
  }

  /**
   * @param wanted the constraint the tool is set to
   */
  protected adoptConstraint(wanted: Constraint): void {
    if (wanted !== this.constraint) {
      this.constrainTo(wanted);
    }
  }

  /**
   * @param constraint what to hold them to
   */
  protected constrainTo(constraint: Constraint): void {
    this.constraint = constraint;
    this.host.reference.refreshGuide();
    const grab = this.grab;
    if (grab?.dragging) {
      grab.members.forEach((member) => (member.start = member.node.at.clone()));
      grab.point = pointOnConstraint(grab.ray, grab.members[0]!.start, this.constraint, grab.viewDirection, this.dragBasis()) ?? grab.point;
    }
  }

  /**
   * Asks for moves to be held to a constraint, as a key press does.
   *
   * @param asked what the key asks for
   */
  protected holdTo(asked: Constraint): void {
    this.host.emit((listener) => listener.constraint?.(asked));
  }

  /**
   * Shows the handles the selection calls for.
   *
   * @param options the tool's settings and the selection
   */
  protected arrange(options: ModeOptions): void {
    const { molecule, bonds, scene } = this.host;

    // everything is moved with handles that move the one pose it is all drawn in, and anything less, a molecule among several
    // included, with handles on the selected atoms, which sit in their middle and leave the rest where it is
    const moleculeSelected = options.selection.some((item) => item.kind === "molecule");
    const atomsSelected = options.selection.some((item) => item.kind === "atom");
    const everything = moleculeSelected && selectsEveryMolecule(options.selection, options.index?.state.fragments.length ?? 1);
    const handleMode = everything ? "translate" : moleculeSelected || atomsSelected ? "atoms" : null;
    if (handleMode !== this.handleMode) {
      this.handleMode = handleMode;
      molecule.setMode(handleMode);
    }

    bonds.set(lengthTarget(options, scene.locked));
  }

  override reset(): void {
    this.grab = null;
    this.host.lock.release("atomDrag");
  }

  override held(): string[] {
    return this.grab?.dragging ? this.grab.members.map((member) => member.node.atom.id) : [];
  }

  override keys(): Binding[] {
    return [
      {
        ...KEYS.constrain,
        run: (event) => {
          // a selection with a locked atom in it cannot be moved, so there is nothing for a constraint to hold
          if (this.selectedLocked()) {
            return;
          }
          const asked = constraintForKey(this.constraint, event.key, event.shiftKey);
          if (asked) {
            this.holdTo(asked);
          }
        },
      },
      { ...KEYS.toggleCarry, run: () => this.host.emit((listener) => listener.carryHydrogens?.(!this.carryHydrogens)) },
    ];
  }

  override press(event: PointerEvent): boolean {
    this.startGrab(event);
    return super.press(event);
  }

  override move(event: PointerEvent): void {
    const grab = this.grab;
    if (!grab) {
      super.move(event);
      return;
    }

    if (!grab.dragging) {
      if (Math.hypot(event.clientX - grab.from.x, event.clientY - grab.from.y) <= CLICK_SLOP) {
        return;
      }
      grab.dragging = true;
      this.host.reference.refreshGuide();
      this.host.emit((listener) => listener.dragStart?.(grab.id));
    }

    grab.ray = this.localRayOf(event);
    const moves = this.placesOf(grab, grab.ray, this.snapping(event));
    if (moves) {
      this.host.moveAtoms(moves.map((move) => [move.id, move.position]));
      this.host.emit((listener) => listener.drag?.(moves, false));
    }
  }

  override release(event: PointerEvent): void {
    const grab = this.grab;
    if (grab) {
      this.releaseGrab();
      if (grab.dragging) {
        this.down = null;
        // a pointer that has left what the drag is held to leaves the atoms where they are
        const moves =
          this.placesOf(grab, this.localRayOf(event), this.snapping(event)) ??
          grab.members.map((member): AtomMove => ({ id: member.node.atom.id, position: toVec3(member.node.at) }));
        this.host.moveAtoms(moves.map((move) => [move.id, move.position]));
        this.host.reference.showSnap(null);
        this.host.emit((listener) => listener.drag?.(moves, true));
        return;
      }
    }

    super.release(event);
  }

  override cursor(_event: PointerEvent): string | null {
    const pick = this.hoveredPick;
    // an atom the move tool would refuse to pick up, because it or one moving with it is locked, says so
    const refused = pick?.kind === "atom" && this.groupOf(pick.id).some((id) => this.host.scene.locked.has(id));
    return refused ? "not-allowed" : this.resting();
  }

  override cancelDrag(): void {
    this.abandonSelectionBox();
    const grab = this.grab;
    if (!grab) {
      return;
    }
    this.releaseGrab();

    if (grab.dragging) {
      const moves = grab.members.map((member): AtomMove => ({ id: member.node.atom.id, position: toVec3(member.original) }));
      this.host.moveAtoms(moves.map((move) => [move.id, move.position]));
      this.host.emit((listener) => listener.dragCancel?.(moves));
    }
  }

  /**
   * Adds the hydrogens that go with some atoms when they are moved, if that is on.
   *
   * @param ids the atoms to move
   * @returns them, and, when carrying hydrogens, the terminal hydrogens bonded to them
   */
  withCarried(ids: Iterable<string>): string[] {
    if (!this.carryHydrogens) {
      return [...ids];
    }

    const { scene } = this.host;
    return withTerminalHydrogens(ids, (id) => {
      const node = scene.atomById.get(id);
      return node && { id, element: node.atom.element, neighbours: node.neighbours.flatMap((at) => scene.atoms[at]?.atom.id ?? []) };
    });
  }

  /** Whether a locked atom is among the selected ones, or goes with them, which keeps the selection from being moved. */
  selectionLocked(): boolean {
    const { scene } = this.host;
    return this.withCarried(scene.emphasis.selected.atoms).some((id) => scene.locked.has(id));
  }

  /** Where the move guide is drawn in the scene. */
  guideTarget(): Vector3 | null {
    const { scene, molecule } = this.host;
    const handles = molecule.position;
    if (handles) {
      return handles;
    }

    const held = this.grab?.dragging ? this.grab.members[0]!.node : null;
    const [active] = scene.emphasis.active.atoms;
    const node = held ?? (active === undefined ? null : (scene.atomById.get(active) ?? null));
    return node && scene.worldOf(node);
  }

  /** Whether the guide for a move is to be drawn. */
  guideWanted(): boolean {
    return this.host.annotations.debug && this.active && this.guideTarget() !== null && !this.selectionLocked();
  }

  /** Whether a selected atom is locked, without counting the hydrogens that would go with it. */
  private selectedLocked(): boolean {
    const { scene } = this.host;
    return [...scene.emphasis.selected.atoms].some((id) => scene.locked.has(id));
  }

  /**
   * The atoms a press on an atom would pick up: the whole selection if the atom is one of several selected, and
   * otherwise only the atom, with the hydrogens bonded to them if those are being carried.
   *
   * @param id the atom pressed on
   * @returns the identifiers of the atoms that would move
   */
  protected groupOf(id: string): string[] {
    const selected = this.host.scene.emphasis.selected.atoms;
    return this.withCarried(selected.has(id) && selected.size > 1 ? selected : [id]);
  }

  /**
     * @returns the directions of the axes a constraint names, in the molecule's own frame
     */
  private dragBasis(): Basis {
    if (this.host.space() === "local") {
      return STANDARD_BASIS;
    }

    // the molecule's rotation takes its frame to the scene's, so the inverse takes the scene's axes into its frame
    const inverse = this.host.scene.content.quaternion.clone().invert();
    return {
      x: STANDARD_BASIS.x.clone().applyQuaternion(inverse),
      y: STANDARD_BASIS.y.clone().applyQuaternion(inverse),
      z: STANDARD_BASIS.z.clone().applyQuaternion(inverse),
    };
  }

  /** The pointer's ray in the molecule's own frame, which is the one atoms are dragged in. */
  protected localRayOf(event: PointerEvent): Ray {
    const { stage, scene } = this.host;
    this.raycaster.setFromCamera(stage.pointer(event), stage.camera);
    return scene.localRay(this.raycaster.ray.clone());
  }

  /**
   * Where the grabbed atoms are, given where the pointer is.
   *
   * @param grab the drag
   * @param ray the pointer's ray
   * @param snapping whether the atom in hand is to land on the nearest point of the grid
   * @returns each atom's place
   */
  private placesOf(grab: Grab, ray: Ray, snapping: boolean): AtomMove[] | null {
    const [lead] = grab.members as [Member, ...Member[]];
    const point = pointOnConstraint(ray, lead.start, this.constraint, grab.viewDirection, this.dragBasis());
    if (!point) {
      return null;
    }

    const target = dragged(grab.point, point, lead.start);
    // the others keep their places around the atom in hand, so they move by as much as it does
    const snapped = snapping ? this.host.reference.snap(target, this.constraint) : null;
    this.host.reference.showSnap(snapped && this.host.scene.content.localToWorld(snapped.clone()));
    const offset = (snapped ?? target).sub(lead.start);
    return grab.members.map((member) => ({ id: member.node.atom.id, position: toVec3(member.start.clone().add(offset)) }));
  }

  /** Whether the atom in hand snaps to the grid: it does if that is on and a grid is shown, unless Ctrl or Cmd is held. */
  private snapping(event: PointerEvent): boolean {
    return this.snap !== (event.ctrlKey || event.metaKey) && this.host.reference.gridShown;
  }

  /** Lets go of the atom in hand, giving the camera its controls back. */
  private releaseGrab(): void {
    this.grab = null;
    this.host.reference.showSnap(null);
    this.host.lock.release("atomDrag");
    this.host.reference.refreshGuide();
  }

  /** Whether a press on an atom takes hold of it, which becomes a drag once the pointer has moved. */
  protected get grabs(): boolean {
    return true;
  }

  /** Takes hold of an atom pressed on, which becomes a drag once the pointer has moved. */
  private startGrab(event: PointerEvent): void {
    if (event.button !== 0 || !this.grabs || (event.shiftKey && this.selectArea)) {
      return;
    }

    const { scene, stage } = this.host;
    const pick = scene.pickAt(event);
    const node = pick?.kind === "atom" ? scene.atomById.get(pick.id) : undefined;
    if (!pick || !node || scene.locked.has(pick.id)) {
      return;
    }

    // an atom in a selection of several takes the rest with it; one outside the selection goes alone
    const group = this.groupOf(pick.id);
    const nodes = [node, ...group.filter((id) => id !== pick.id).map((id) => scene.atomById.get(id))];
    if (nodes.some((each) => !each || scene.locked.has(each.atom.id))) {
      return;
    }

    const ray = this.localRayOf(event);
    const viewDirection = stage.camera.getWorldDirection(new Vector3()).applyQuaternion(scene.content.quaternion.clone().invert());
    const point = pointOnConstraint(ray, node.at, this.constraint, viewDirection, this.dragBasis());
    if (!point) {
      return;
    }

    this.grab = {
      id: pick.id,
      members: (nodes as AtomNode[]).map((each) => ({ node: each, original: each.at.clone(), start: each.at.clone() })),
      point,
      viewDirection,
      from: { x: event.clientX, y: event.clientY },
      ray,
      dragging: false,
    };

    // the camera must not turn while the atom does, and the pointer must keep reporting if it leaves the canvas
    this.host.lock.hold("atomDrag");
    stage.canvas.setPointerCapture(event.pointerId);
  }
}
