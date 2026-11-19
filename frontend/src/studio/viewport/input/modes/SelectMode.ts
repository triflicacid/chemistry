import {Vector3} from "three";
import type {Tool} from "../../../models/ToolModel";
import type {Binding} from "../../../keys/binding";
import type {PartItem} from "../../../models/items";
import type {ScreenPoint, ScreenRect} from "../../StudioViewListener";
import {CLICK_SLOP, DOUBLE_CLICK_MS} from "../constants";
import type {ModeHost, ModeOptions, ToolMode} from "./ToolMode";

export class SelectMode implements ToolMode {
  readonly swallows = false;

  get dragging(): boolean {
    return this.boxing;
  }

  /** The rectangle Shift and a left-drag draws. */
  private selectionBox: { from: ScreenPoint; active: boolean } | null = null;

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

  protected get centresOnDoubleClick(): boolean {
    return true;
  }

  /** Is the shift+click select rectangle enabled? */
  protected get selectArea(): boolean {
    return this.tool === "select" || this.tool === "move" || this.tool === "rotate";
  }

  /** Whether a rectangle is being drawn. */
  protected get boxing(): boolean {
    return this.selectionBox?.active ?? false;
  }

  enter(): void {}

  exit(): void {
    this.abandonSelectionBox();
  }

  configure(_options: ModeOptions): void {}

  reset(): void {}

  keys(): Binding[] {
    return [];
  }

  held(): string[] {
    return [];
  }

  cancelDrag(): void {
    this.abandonSelectionBox();
  }

  resting(): string {
    return "";
  }

  hitTest(): boolean {
    return true;
  }

  press(event: PointerEvent): boolean {
    this.down = event.button === 0 ? { x: event.clientX, y: event.clientY } : null;
    this.rightDown = event.button === 2 ? { x: event.clientX, y: event.clientY } : null;

    if (event.button === 0 && event.shiftKey && this.selectArea) {
      this.selectionBox = { from: { x: event.clientX, y: event.clientY }, active: false };
      // we don't want to pan the camera
      this.host.lock.hold("marquee");
      this.host.stage.canvas.setPointerCapture(event.pointerId);
    }
    return true;
  }

  move(event: PointerEvent): void {
    const box = this.selectionBox;
    if (!box) {
      return;
    }

    if (!box.active) {
      if (Math.hypot(event.clientX - box.from.x, event.clientY - box.from.y) <= CLICK_SLOP) {
        return;
      }
      box.active = true;
      this.host.stage.canvas.style.cursor = "crosshair";
    }
    this.host.emit((listener) => listener.updateSelectionArea?.(this.rectTo(box.from, event)));
  }

  release(event: PointerEvent): void {
    if (this.finishSelectionBox(event)) {
      return;
    }
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

  private finishSelectionBox(event: PointerEvent): boolean {
    const box = this.selectionBox;
    if (!box) {
      return false;
    }

    this.putAwaySelectionBox();
    if (!box.active) {
      return false;
    }

    // the release that ends a rectangle must not also be a click
    this.down = null;
    const rect = this.rectTo(box.from, event);
    const canvas = this.host.stage.canvas.getBoundingClientRect();
    const { scene, stage } = this.host;
    const inside = scene.atoms.filter((node) => {
      const at = this.screenOf(node.at);
      const x = at.x - canvas.left;
      const y = at.y - canvas.top;
      return x >= rect.x && x <= rect.x + rect.width && y >= rect.y && y <= rect.y + rect.height;
    });

    // nearest the camera first, which is the order a ray through the rectangle meets them in, and the first is what decides
    // which molecule the rectangle is selecting from
    const forward = stage.camera.getWorldDirection(new Vector3());
    const depthOf = (node: (typeof inside)[number]) => scene.worldOf(node).sub(stage.camera.position).dot(forward);
    const nearestFirst = [...inside].sort((a, b) => depthOf(a) - depthOf(b));
    this.host.emit((listener) => listener.releaseSelectionArea?.(nearestFirst.map((node) => node.atom.id)));
    return true;
  }

  /** Gives up a rectangle without selecting anything. */
  protected abandonSelectionBox(): void {
    if (this.selectionBox) {
      this.putAwaySelectionBox();
    }
  }

  /** Takes the rectangle away and gives the camera back. */
  private putAwaySelectionBox(): void {
    this.selectionBox = null;
    this.host.lock.release("marquee");
    this.host.stage.canvas.style.cursor = this.resting();
    this.host.emit((listener) => listener.updateSelectionArea?.(null));
  }

  /**
   * @param from where the rectangle began
   * @param event where the pointer is now
   * @returns the rectangle between them, in pixels from the canvas's top left corner
   */
  private rectTo(from: ScreenPoint, event: PointerEvent): ScreenRect {
    const canvas = this.host.stage.canvas.getBoundingClientRect();
    return {
      x: Math.min(from.x, event.clientX) - canvas.left,
      y: Math.min(from.y, event.clientY) - canvas.top,
      width: Math.abs(event.clientX - from.x),
      height: Math.abs(event.clientY - from.y),
    };
  }

  /**
   * @param local a place in the molecule's own frame
   * @returns where it is on the screen, in the same pixels as a pointer event
   */
  protected screenOf(local: Vector3): ScreenPoint {
    const { stage, scene } = this.host;
    const ndc = scene.content.localToWorld(local.clone()).project(stage.camera);
    const box = stage.canvas.getBoundingClientRect();
    return { x: box.left + ((ndc.x + 1) / 2) * box.width, y: box.top + ((1 - ndc.y) / 2) * box.height };
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
    if (this.centresOnDoubleClick && last && now - last.at < DOUBLE_CLICK_MS && Math.hypot(event.clientX - last.x, event.clientY - last.y) <= CLICK_SLOP) {
      this.lastClick = null;
      this.centreOn(event);
      return;
    }
    this.lastClick = { x: event.clientX, y: event.clientY, at: now };

    if (this.picks) {
      this.clicked(event, this.host.scene.pickAt(event));
    }
  }

  /** Answers a left click that was not a camera drag. */
  protected clicked(event: PointerEvent, pick: PartItem | null): void {
    this.host.emit((listener) => listener.click?.(pick, event.shiftKey || event.ctrlKey || event.metaKey));
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
