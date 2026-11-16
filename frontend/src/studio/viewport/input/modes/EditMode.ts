import {Vector3} from "three";
import type {Vec3} from "../../../../api/types/structure";
import type {PartItem} from "../../../models/items";
import type {EditClick} from "../../../models/ToolModel";
import {type Constraint, pointOnConstraint} from "../../handles/dragPlane";
import {toVec3} from "../../vec";
import {MoveMode} from "./MoveMode";
import type {ModeHost, ModeOptions} from "./ToolMode";

export class EditMode extends MoveMode {
  /** Whether a click adds an atom, or selects what it is on. */
  private editClick: EditClick = "add";

  /**
   * @param host the view the mode is for
   */
  constructor(host: ModeHost) {
    super(host, "edit");
  }

  // clicking quickly adds an atom each time, and must not instead be taken for a request to centre the view
  protected override get centresOnDoubleClick(): boolean {
    return this.editClick === "select";
  }

  // while a click selects, a drag turns the camera as it does with the select tool
  protected override get grabs(): boolean {
    return this.editClick === "add";
  }

  override configure(options: ModeOptions): void {
    // there is nowhere to switch snapping on here, so a setting made for the move tool is not in force
    super.configure({ ...options, snap: false });
    this.editClick = options.editClick;
    this.host.stage.canvas.style.cursor = this.resting();
  }

  // a constraint here belongs to one drag, so the move tool's setting is neither taken nor changed
  protected override adoptConstraint(_wanted: Constraint): void {}

  protected override holdTo(asked: Constraint): void {
    this.constrainTo(asked);
  }

  // there are no handles on the molecule, atoms or bonds
  protected override arrange(_options: ModeOptions): void {}

  override exit(): void {
    this.letGo();
    super.exit();
  }

  override release(event: PointerEvent): void {
    super.release(event);
    this.letGo();
  }

  override cancelDrag(): void {
    super.cancelDrag();
    this.letGo();
  }

  override resting(): string {
    return this.editClick === "add" ? "crosshair" : "";
  }

  override cursor(event: PointerEvent): string | null {
    return this.editClick === "add" ? super.cursor(event) : this.resting();
  }

  // the guide marks a drag in hand, and nothing else: a free drag is in the view's own plane, which is nothing to mark
  override guideWanted(): boolean {
    return this.dragging && this.constraint !== "free" && this.guideTarget() !== null;
  }

  /** Puts the constraint of a drag down, so that the next one is free unless a key says otherwise. */
  private letGo(): void {
    if (this.constraint !== "free") {
      this.constraint = "free";
      this.host.reference.refreshGuide();
    }
  }

  protected override clicked(event: PointerEvent, pick: PartItem | null): void {
    const additive = event.shiftKey || event.ctrlKey || event.metaKey;
    if (this.editClick === "select" || additive || pick?.kind === "bond") {
      super.clicked(event, pick);
      return;
    }

    if (pick?.kind === "atom") {
      this.host.emit((listener) => listener.place?.({ attach: pick.id }));
    } else {
      const position = this.freePosition(event);
      this.host.emit((listener) => listener.place?.({ position }));
    }
  }

  /**
   * Finds where a free atom goes for a click on empty space.
   *
   * @param event the click
   * @returns the place, in the molecule's own frame
   */
  private freePosition(event: PointerEvent): Vec3 {
    const { stage, scene } = this.host;
    const ray = this.localRayOf(event);
    const view = stage.camera.getWorldDirection(new Vector3()).applyQuaternion(scene.content.quaternion.clone().invert());
    const middle = scene.content.worldToLocal(scene.middleOfAtoms());
    return toVec3(pointOnConstraint(ray, middle, "free", view) ?? middle);
  }
}
