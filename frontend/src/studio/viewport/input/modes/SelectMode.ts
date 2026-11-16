import type {Vector3} from "three";
import type {Tool} from "../../../models/ToolModel";
import type {Binding} from "../../../keys/binding";
import type {PartItem} from "../../../models/items";
import type {ScreenPoint} from "../../StudioViewListener";
import {CLICK_SLOP, DOUBLE_CLICK_MS} from "../constants";
import type {ModeHost, ModeOptions, ToolMode} from "./ToolMode";

export class SelectMode implements ToolMode {
  readonly swallows = false;

  get dragging(): boolean {
    return false;
  }

  /** Where the left button was pressed. */
  protected down: ScreenPoint | null = null;
  /** Where the right button was pressed. */
  private rightDown: ScreenPoint | null = null;
  private lastClick: (ScreenPoint & { at: number }) | null = null;
  /** What the pointer is over in the molecule, found as it moves. */
  protected hoveredPick: PartItem | null = null;

  /**
   * @param host the view the mode is for
   * @param tool the tool the mode is for
   */
  constructor(
    protected readonly host: ModeHost,
    readonly tool: Tool = "select",
  ) {}

  /** Whether hovering and clicking say what is under the pointer, which a mode that only moves the view does not. */
  protected get picks(): boolean {
    return true;
  }

  enter(): void {}

  exit(): void {}

  configure(_options: ModeOptions): void {}

  reset(): void {}

  keys(): Binding[] {
    return [];
  }

  held(): string[] {
    return [];
  }

  cancelDrag(): void {}

  resting(): string {
    return "";
  }

  hitTest(): boolean {
    return true;
  }

  press(event: PointerEvent): boolean {
    this.down = event.button === 0 ? { x: event.clientX, y: event.clientY } : null;
    this.rightDown = event.button === 2 ? { x: event.clientX, y: event.clientY } : null;
    return true;
  }

  release(event: PointerEvent): void {
    this.click(event);
    this.rightClick(event);
  }

  cancel(): void {
    this.down = null;
    this.cancelDrag();
  }

  hover(event: PointerEvent): void {
    this.hoveredPick = this.host.scene.pickAt(event);
    if (this.picks) {
      this.host.emit((listener) => listener.hover?.(this.hoveredPick, { x: event.clientX, y: event.clientY }));
    }
  }

  cursor(_event: PointerEvent): string | null {
    return this.resting();
  }

  leave(): void {
    this.host.stage.canvas.style.cursor = this.resting();
    if (this.picks) {
      this.host.emit((listener) => listener.hover?.(null, { x: 0, y: 0 }));
    }
  }

  private click(event: PointerEvent): void {
    const down = this.down;
    this.down = null;
    // a left drag orbits the camera, and the release that ends one must not also select what is under it
    if (event.button !== 0 || !down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > CLICK_SLOP) {
      return;
    }

    // the second click of a double-click centres the view and is not also a single click
    const now = performance.now();
    const last = this.lastClick;
    if (last && now - last.at < DOUBLE_CLICK_MS && Math.hypot(event.clientX - last.x, event.clientY - last.y) <= CLICK_SLOP) {
      this.lastClick = null;
      this.centreOn(event);
      return;
    }
    this.lastClick = { x: event.clientX, y: event.clientY, at: now };

    if (this.picks) {
      const pick = this.host.scene.pickAt(event);
      this.host.emit((listener) => listener.click?.(pick, event.shiftKey || event.ctrlKey || event.metaKey));
    }
  }

  private rightClick(event: PointerEvent): void {
    const down = this.rightDown;
    this.rightDown = null;
    if (event.button !== 2 || !down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > CLICK_SLOP) {
      return;
    }

    this.centreOn(event);
  }

  /**
   * Brings the atom nearest where the pointer is to the middle of the view.
   *
   * @param event the pointer event that says where
   */
  private centreOn(event: PointerEvent): void {
    const { scene, stage, rig } = this.host;
    const hit = scene.hitPoint(event);
    let nearest: Vector3 | null = null;
    let best = Infinity;
    if (hit) {
      for (const node of scene.atoms) {
        const at = scene.worldOf(node);
        const distance = at.distanceToSquared(hit);
        if (distance < best) {
          best = distance;
          nearest = at;
        }
      }
    }

    const anchored = nearest?.clone() ?? scene.middleOfAtoms();
    stage.pivotOn(anchored);
    rig.anchorOn(anchored);
  }
}
