import {Quaternion, Vector3} from "three";
import type {Vec3} from "../../../../api/types/structure";
import {aimedAt, clampRing, ringBondLength, ringLayout, type RingLayout, roomFor} from "../../../geometry/ring";
import type {Binding} from "../../../keys/binding";
import {KEYS} from "../../../keys/keymap";
import type {PartItem} from "../../../models/items";
import type {EditClick} from "../../../models/ToolModel";
import type {BondOrder} from "../../../session/messages";
import {type Constraint, pointOnConstraint} from "../../handles/dragPlane";
import type {Ghost} from "../../scene/RingGuide";
import type {ScreenPoint} from "../../StudioViewListener";
import {toVec3} from "../../vec";
import {CLICK_SLOP} from "../constants";
import {MoveMode} from "./MoveMode";
import type {ModeHost, ModeOptions} from "./ToolMode";

/** How near the pointer must be to a ghost atom, in pixels, for a click to mean it. */
const GHOST_PICK_PX = 26;

/** How soon after one turn of the wheel another is ignored. */
const WHEEL_MS = 90;

/** A ring being grown from an atom. */
interface Ring {
  /** The atom the ring grows from, which is the first place on it. */
  source: string;
  /** The way from the source to the middle of the ring, a unit vector in the ring's plane. */
  towards: Vector3;
  /** At right angles to the ring's plane. It is the view's until the first atom is placed, and after that the ring stays where it is. */
  normal: Vector3;
  /** How many atoms the ring has. */
  size: number;
  /** The atoms placed on the ring so far, in the order they were placed. */
  placed: string[];
  /** The place on the ring each of them took, numbering the source 0. */
  indices: number[];
  /** Which way round the ring the atoms are being placed, or 0 until the first has been. */
  direction: 0 | 1 | -1;
  /** Whether an atom is being placed. */
  busy: boolean;
}

/** A press on an atom that may turn into a drag, which aims the ring at the pointer. */
interface Aim {
  source: string;
  from: ScreenPoint;
  dragging: boolean;
}

/** A mode for editing the structure: adding/removing bonds/atoms, etc. */
export class EditMode extends MoveMode {
  /** What a click does. */
  private editClick: EditClick = "add";
  private ringSize = 6;
  private bondOrder: BondOrder = "SINGLE";
  /** The ring being grown, or null while there is none. */
  private ring: Ring | null = null;
  /** The press that may turn into an aim, or null while there is none. */
  private aim: Aim | null = null;
  /** The place on the ring the pointer is over, or null for none. */
  private ghostHover: number | null = null;
  /** The atoms of the ring seen in the scene, so that one that is gone later can be told from one not drawn yet. */
  private seen = new Set<string>();
  private lastWheel = 0;

  /**
   * @param host the view the mode is for
   */
  constructor(host: ModeHost) {
    super(host, "edit");
  }

  // clicking quickly adds an atom or places one on the ring each time, and must not instead be taken for a request to centre the view
  protected override get centresOnDoubleClick(): boolean {
    return this.editClick === "select";
  }

  // otherwise a drag turns the camera, as it does with the select tool, or aims the ring
  protected override get grabs(): boolean {
    return this.editClick === "add";
  }

  protected override get selectArea(): boolean {
    return this.editClick === "select";
  }

  override get dragging(): boolean {
    return super.dragging || this.boxing;
  }

  override enter(): void {
    super.enter();
    this.host.stage.canvas.addEventListener("wheel", this.onWheel, { capture: true, passive: false });
  }

  override exit(): void {
    this.host.stage.canvas.removeEventListener("wheel", this.onWheel, { capture: true });
    this.dismiss();
    this.letGo();
    this.dropAim();
    this.abandonSelectionBox();
    super.exit();
  }

  override configure(options: ModeOptions): void {
    // there is nowhere to switch snapping on here, so a setting made for the move tool is not in force
    super.configure({ ...options, snap: false });
    this.editClick = options.editClick;
    this.ringSize = options.ringSize;
    this.bondOrder = options.bondOrder;
    this.host.stage.canvas.style.cursor = this.resting();

    const ring = this.ring;
    if (ring) {
      this.keepRing(ring);
    }
  }

  // a constraint here belongs to one drag, so the move tool's setting is neither taken nor changed
  protected override adoptConstraint(_wanted: Constraint): void {}

  protected override holdTo(asked: Constraint): void {
    this.constrainTo(asked);
  }

  // there are no handles on the molecule, atoms or bonds
  protected override arrange(_options: ModeOptions): void {}

  override keys(): Binding[] {
    return super.keys().filter((binding) => binding.key !== KEYS.toggleCarry.key);
  }

  override reset(): void {
    super.reset();
    this.dropAim();
  }

  override press(event: PointerEvent): boolean {
    this.dropAim();
    if (this.editClick === "ring" && event.button === 0 && !(event.shiftKey || event.ctrlKey || event.metaKey)) {
      const pick = this.host.scene.pickAt(event);
      // before an atom is placed, any atom can be where the ring grows from, and a drag from it turns the ring about it
      if (pick?.kind === "atom" && (this.ring === null || this.ring.placed.length === 0)) {
        this.aim = { source: pick.id, from: { x: event.clientX, y: event.clientY }, dragging: false };
        // the camera must not turn while the ring does
        this.host.lock.hold("ringAim");
        this.host.stage.canvas.setPointerCapture(event.pointerId);
      }
    }
    return super.press(event);
  }

  override move(event: PointerEvent): void {
    const aim = this.aim;
    if (!aim) {
      super.move(event);
      return;
    }

    if (!aim.dragging) {
      if (Math.hypot(event.clientX - aim.from.x, event.clientY - aim.from.y) <= CLICK_SLOP) {
        return;
      }
      aim.dragging = true;
      if (this.ring?.source !== aim.source) {
        this.startRing(aim.source);
      }
    }
    this.turn(event);
  }

  override release(event: PointerEvent): void {
    const aim = this.aim;
    this.dropAim();
    if (aim?.dragging) {
      // a drag that aimed the ring is not also a click
      this.down = null;
      return;
    }

    super.release(event);
    this.letGo();
  }

  override cancelDrag(): void {
    super.cancelDrag();
    this.dropAim();
    this.abandonSelectionBox();
    this.letGo();
  }

  override hover(event: PointerEvent): void {
    super.hover(event);
    const ring = this.ring;
    if (ring && !ring.busy) {
      const over = this.ghostAt(event, ring);
      if (over !== this.ghostHover) {
        this.ghostHover = over;
        this.render();
      }
    }
  }

  override resting(): string {
    return this.editClick === "select" ? "" : "crosshair";
  }

  override cursor(event: PointerEvent): string | null {
    if (this.ghostHover !== null) {
      return "pointer";
    }
    return this.editClick === "add" ? super.cursor(event) : this.resting();
  }

  // the guide marks a drag in hand, and nothing else: a free drag is in the view's own plane, which is nothing to mark
  override guideWanted(): boolean {
    return this.dragging && this.constraint !== "free" && this.guideTarget() !== null;
  }

  /**
   * Puts the ring away, leaving any atoms placed on it.
   *
   * @returns whether there was a ring
   */
  dismiss(): boolean {
    const had = this.ring !== null;
    this.ring = null;
    this.ghostHover = null;
    this.seen.clear();
    this.host.ringGuide.hide();
    return had;
  }

  protected override clicked(event: PointerEvent, pick: PartItem | null): void {
    const additive = event.shiftKey || event.ctrlKey || event.metaKey;
    if (this.editClick === "select" || additive || pick?.kind === "bond") {
      super.clicked(event, pick);
      return;
    }

    if (this.editClick === "ring") {
      void this.clickRing(event, pick);
    } else if (pick?.kind === "atom") {
      void this.host.place({ attach: pick.id });
    } else {
      void this.host.place({ position: this.freePosition(event) });
    }
  }

  /** Puts the constraint of a drag down, so that the next one is free unless a key says otherwise. */
  private letGo(): void {
    if (this.constraint !== "free") {
      this.constraint = "free";
      this.host.reference.refreshGuide();
    }
  }

  /** Lets go of a press that might have aimed the ring, giving the camera back. */
  private dropAim(): void {
    if (this.aim) {
      this.aim = null;
      this.host.lock.release("ringAim");
    }
  }

  /** The scroll wheel resizes the ring while no atom has been placed on it, and otherwise leaves the camera to zoom. */
  private readonly onWheel = (event: WheelEvent): void => {
    const ring = this.ring;
    if (this.editClick !== "ring" || !ring || ring.placed.length > 0) {
      return;
    }

    event.preventDefault();
    event.stopImmediatePropagation();
    const now = performance.now();
    if (now - this.lastWheel < WHEEL_MS) {
      return;
    }
    this.lastWheel = now;

    const size = clampRing(ring.size + (event.deltaY < 0 ? 1 : -1));
    if (size !== ring.size) {
      ring.size = size;
      this.ghostHover = null;
      this.render();
      this.host.emit((listener) => listener.ringSize?.(size));
    }
  };

  private async clickRing(event: PointerEvent, pick: PartItem | null): Promise<void> {
    const ring = this.ring;
    if (!ring) {
      if (pick?.kind === "atom") {
        this.startRing(pick.id);
      }
      return;
    }
    if (ring.busy) {
      return;
    }

    const place = this.ghostAt(event, ring);
    if (place !== null) {
      await this.placeOn(ring, place);
    } else if (pick?.kind === "atom" && pick.id !== ring.source && ring.placed.length === 0) {
      // another atom, with nothing placed yet, is where the ring grows from instead
      this.startRing(pick.id);
    }
  }

  /**
   * Adds an atom at one place on the ring, bonded to the one before it.
   *
   * @param ring the ring
   * @param place which place on it, numbering the source 0
   */
  private async placeOn(ring: Ring, place: number): Promise<void> {
    const made = this.layoutOf(ring);
    if (!made) {
      return;
    }

    ring.busy = true;
    const id = await this.host.place({ attach: ring.placed.at(-1) ?? ring.source, position: toVec3(made.vertices[place]!) });
    ring.busy = false;
    // the ring may have been put away while the atom was being made, and an atom that was refused leaves it as it was
    if (this.ring !== ring || id === null) {
      return;
    }

    ring.placed.push(id);
    ring.indices.push(place);
    this.seen.add(id);
    if (ring.direction === 0) {
      ring.direction = place === 1 ? 1 : -1;
    }

    // the last atom closes the ring, which is then done with
    if (ring.placed.length === ring.size - 1) {
      await this.host.join(id, ring.source);
      this.dismiss();
      return;
    }
    this.render();
  }

  /**
   * Starts drawing a ring on an atom, facing into the room its neighbours leave.
   *
   * @param source the atom
   */
  private startRing(source: string): void {
    const { scene } = this.host;
    const node = scene.atomById.get(source);
    if (!node) {
      return;
    }

    const { right, up, normal } = this.viewAxes();
    const arms = node.neighbours.flatMap((at) => {
      const other = scene.atoms[at];
      return other ? [other.at.clone().sub(node.at)] : [];
    });
    this.ring = { source, towards: roomFor(arms, right, up), normal, size: this.ringSize, placed: [], indices: [], direction: 0, busy: false };
    this.ghostHover = null;
    this.seen = new Set([source]);
    this.render();
  }

  /**
   * Turns the ring about its source to face where the pointer is.
   *
   * @param event the pointer
   */
  private turn(event: PointerEvent): void {
    const ring = this.ring;
    const node = ring && this.host.scene.atomById.get(ring.source);
    if (!ring || !node) {
      return;
    }

    const at = this.screenOf(node.at);
    const { right, up, normal } = this.viewAxes();
    const towards = aimedAt(right, up, event.clientX - at.x, event.clientY - at.y);
    if (towards) {
      ring.towards = towards;
      ring.normal = normal;
      this.render();
    }
  }

  /**
   * Gives a ring the size the tool is set to while nothing is placed on it, and puts it away if an atom of it has gone,
   * which an undo or a deletion does.
   *
   * @param ring the ring
   */
  private keepRing(ring: Ring): void {
    const { scene } = this.host;
    if (this.editClick !== "ring") {
      this.dismiss();
      return;
    }

    for (const id of [ring.source, ...ring.placed]) {
      if (scene.atomById.has(id)) {
        this.seen.add(id);
      } else if (this.seen.has(id)) {
        this.dismiss();
        return;
      }
    }

    if (ring.placed.length === 0 && ring.size !== this.ringSize) {
      ring.size = this.ringSize;
    }
    this.render();
  }

  /** Draws the ring as it is, or stops drawing it if there is none. */
  private render(): void {
    const ring = this.ring;
    const made = ring && this.layoutOf(ring);
    if (!ring || !made) {
      this.host.ringGuide.hide();
      return;
    }
    this.host.ringGuide.show(this.host.scene.content, made, this.ghostsOf(ring), this.ghostHover);
  }

  /**
   * @param ring the ring
   * @returns where it is, or null if the atom it grows from is not drawn
   */
  private layoutOf(ring: Ring): RingLayout | null {
    const node = this.host.scene.atomById.get(ring.source);
    return node ? ringLayout(node.at, ring.towards, ring.normal, ring.size, ringBondLength(this.bondOrder)) : null;
  }

  /**
   * @param ring the ring
   * @returns what each of its places is
   */
  private ghostsOf(ring: Ring): Ghost[] {
    const ghosts: Ghost[] = [];
    for (let place = 0; place < ring.size; place++) {
      ghosts.push(place === 0 || ring.indices.includes(place) ? "taken" : this.next(ring, place) ? "next" : "later");
    }
    return ghosts;
  }

  /**
   * @param ring the ring
   * @param place a place on it
   * @returns whether the next atom can go there
   */
  private next(ring: Ring, place: number): boolean {
    if (ring.direction === 0) {
      return place === 1 || place === ring.size - 1;
    }
    return place === (ring.indices.at(-1)! + ring.direction + ring.size) % ring.size;
  }

  /**
   * @param event the pointer
   * @param ring the ring
   * @returns the place the next atom can go that is nearest the pointer and near enough to mean, or null
   */
  private ghostAt(event: PointerEvent, ring: Ring): number | null {
    const made = this.layoutOf(ring);
    if (!made) {
      return null;
    }

    let nearest: number | null = null;
    let best = GHOST_PICK_PX;
    made.vertices.forEach((vertex, place) => {
      if (!this.next(ring, place) || ring.indices.includes(place) || place === 0) {
        return;
      }
      const at = this.screenOf(vertex);
      const distance = Math.hypot(at.x - event.clientX, at.y - event.clientY);
      if (distance <= best) {
        best = distance;
        nearest = place;
      }
    });
    return nearest;
  }

  /** @returns the view's directions in the molecule's own frame: rightwards, upwards, and at right angles to the screen */
  private viewAxes(): { right: Vector3; up: Vector3; normal: Vector3 } {
    const { stage, scene } = this.host;
    const turn = stage.camera.getWorldQuaternion(new Quaternion()).premultiply(scene.content.quaternion.clone().invert());
    return {
      right: new Vector3(1, 0, 0).applyQuaternion(turn),
      up: new Vector3(0, 1, 0).applyQuaternion(turn),
      normal: new Vector3(0, 0, 1).applyQuaternion(turn),
    };
  }

  /**
   * Finds where a free atom goes for a click on empty space: where the pointer's ray meets the plane that faces the camera
   * and passes through the middle of the molecule.
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
