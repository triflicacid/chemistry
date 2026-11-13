import type { Scene, SceneAttributes, SceneElement, ScenePoint } from "../api/types";
import type { Highlights, MoleculeView, Pick, SelectionState, ViewListener } from "./MoleculeView";

const NS = "http://www.w3.org/2000/svg";

const MIN_ZOOM = 0.2;
const MAX_ZOOM = 20;

/** How far the pointer may move with the button down before a click becomes a drag, in pixels. */
const DRAG_THRESHOLD = 4;

/** How far outside an atom or bond the pointer may stray before it is no longer over it, in pixels. */
const HOVER_SLACK = 12;

interface Drag {
  pointer: number;
  startX: number;
  startY: number;
  originX: number;
  originY: number;
  moved: boolean;
}

export class SvgView implements MoleculeView {
  readonly element: SVGSVGElement = document.createElementNS(NS, "svg");
  /** What panning and zooming move; the background stays put. */
  private readonly world = document.createElementNS(NS, "g");
  private selection: SelectionState = { atoms: new Set(), bonds: new Set() };
  private scale = 1;
  private x = 0;
  private y = 0;
  private dragging: Drag | null = null;
  private suppressClick = false;
  private current: Pick | null = null;
  private readonly listeners = new Set<ViewListener>();
  private readonly abort = new AbortController();

  constructor() {
    const listen = { signal: this.abort.signal };
    this.element.addEventListener("wheel", (event) => this.wheel(event), { ...listen, passive: false });
    this.element.addEventListener("pointerdown", (event) => this.down(event), listen);
    this.element.addEventListener("pointermove", (event) => this.move(event), listen);
    this.element.addEventListener("pointerup", (event) => this.up(event), listen);
    this.element.addEventListener("pointercancel", (event) => this.up(event), listen);
    this.element.addEventListener("pointerover", (event) => this.over(event), listen);
    this.element.addEventListener("pointerleave", () => this.leave(), listen);
    this.element.addEventListener("click", (event) => this.click(event), listen);
    this.element.addEventListener("dblclick", () => this.resetView(), listen);
    // a drag must not also count as a click
    this.element.addEventListener(
      "click",
      (event) => {
        if (this.suppressClick) {
          event.stopImmediatePropagation();
          this.suppressClick = false;
        }
      },
      { ...listen, capture: true },
    );
  }

  setScene(scene: Scene): void {
    const { element, world } = this;
    element.replaceChildren();
    element.setAttribute("width", String(scene.width));
    element.setAttribute("height", String(scene.height));
    element.setAttribute("viewBox", `0 0 ${scene.width} ${scene.height}`);
    SvgView.apply(element, scene.attributes);

    if (scene.gradients.length > 0) {
      const defs = document.createElementNS(NS, "defs");
      for (const gradient of scene.gradients) {
        const node = document.createElementNS(NS, "linearGradient");
        node.setAttribute("id", gradient.id);
        node.setAttribute("gradientUnits", "userSpaceOnUse");
        node.setAttribute("x1", String(gradient.from.x));
        node.setAttribute("y1", String(gradient.from.y));
        node.setAttribute("x2", String(gradient.to.x));
        node.setAttribute("y2", String(gradient.to.y));
        for (const stop of gradient.stops) {
          const entry = document.createElementNS(NS, "stop");
          entry.setAttribute("offset", String(stop.offset));
          entry.setAttribute("stop-color", stop.colour);
          node.append(entry);
        }
        defs.append(node);
      }
      element.append(defs);
    }

    if (scene.background) {
      const background = document.createElementNS(NS, "rect");
      background.setAttribute("width", String(scene.width));
      background.setAttribute("height", String(scene.height));
      background.setAttribute("fill", scene.background);
      element.append(background);
    }

    world.replaceChildren(...scene.elements.map((child) => SvgView.build(child)));
    element.append(world);
    this.resetView();
    this.setSelection(this.selection);
  }

  listen(listener: ViewListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private over(event: PointerEvent): void {
    const target = this.pick(event.target);
    // leaving onto the bare canvas is left to move(), which allows the pointer some slack
    if (!target || target.handle === this.current?.handle) {
      return;
    }
    this.current = target;
    this.emitHover(target, event);
  }

  private leave(): void {
    if (this.current) {
      this.current = null;
      this.emitHover(null, null);
    }
  }

  private click(event: MouseEvent): void {
    const target = this.pick(event.target);
    this.listeners.forEach((listener) => listener.click?.(target, event.shiftKey));
  }

  private emitHover(target: Pick | null, at: MouseEvent | null): void {
    const point = { x: at?.clientX ?? 0, y: at?.clientY ?? 0 };
    this.listeners.forEach((listener) => listener.hover?.(target, point));
  }

  private pick(target: EventTarget | null): Pick | null {
    const element = (target as Element | null)?.closest?.<SVGElement>(".atom, .bond");
    if (!element || !this.element.contains(element)) {
      return null;
    }
    const kind = element.classList.contains("atom") ? "atom" : "bond";
    const index = element.dataset[kind];
    return { kind, index: index === undefined ? null : Number(index), data: { ...element.dataset } as Record<string, string>, handle: element };
  }

  setSelection(selection: SelectionState): void {
    this.selection = selection;
    for (const [kind, chosen] of [["atom", selection.atoms], ["bond", selection.bonds]] as const) {
      for (const node of this.element.querySelectorAll<SVGElement>(`.${kind}[data-${kind}]`)) {
        node.classList.toggle("selected", chosen.has(Number(node.dataset[kind])));
      }
    }
  }

  setHighlights(highlights: Highlights): void {
    this.element.querySelectorAll(".hover, .hover-end-1, .hover-end-2").forEach((node) => node.classList.remove("hover", "hover-end-1", "hover-end-2"));
    highlights.hover && (highlights.hover.handle as Element).classList.add("hover");
    for (const { atom, colour } of highlights.ends) {
      this.element.querySelector(`.atom[data-atom="${atom}"]`)?.classList.add(`hover-end-${colour}`);
    }
  }

  resetView(): void {
    this.scale = 1;
    this.x = 0;
    this.y = 0;
    this.transform();
  }

  dispose(): void {
    this.abort.abort();
  }

  private wheel(event: WheelEvent): void {
    event.preventDefault();
    const scale = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, this.scale * Math.exp(-event.deltaY * 0.0015)));
    const at = this.toScene(event);
    // keep the point under the pointer where it is
    this.x = at.x - ((at.x - this.x) * scale) / this.scale;
    this.y = at.y - ((at.y - this.y) * scale) / this.scale;
    this.scale = scale;
    this.transform();
  }

  private down(event: PointerEvent): void {
    if (event.button !== 0) {
      return;
    }
    this.dragging = {
      pointer: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      originX: this.x,
      originY: this.y,
      moved: false,
    };
  }

  /** Keeps the hover going while the pointer stays near the part it is over, and ends it when it strays. */
  private track(event: PointerEvent): void {
    if (!this.current) {
      return;
    }
    const box = (this.current.handle as Element).getBoundingClientRect();
    const near =
      event.clientX >= box.left - HOVER_SLACK &&
      event.clientX <= box.right + HOVER_SLACK &&
      event.clientY >= box.top - HOVER_SLACK &&
      event.clientY <= box.bottom + HOVER_SLACK;
    if (near) {
      this.emitHover(this.current, event);
    } else {
      this.current = null;
      this.emitHover(null, null);
    }
  }

  private move(event: PointerEvent): void {
    this.track(event);
    const drag = this.dragging;
    if (!drag || drag.pointer !== event.pointerId) {
      return;
    }
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) {
      return;
    }
    if (!drag.moved) {
      drag.moved = true;
      this.element.setPointerCapture(event.pointerId);
    }
    const unit = this.pixelsPerUnit();
    this.x = drag.originX + dx / unit;
    this.y = drag.originY + dy / unit;
    this.transform();
  }

  private up(event: PointerEvent): void {
    const drag = this.dragging;
    if (!drag || drag.pointer !== event.pointerId) {
      return;
    }
    this.dragging = null;
    this.suppressClick = drag.moved;
    if (drag.moved && this.element.hasPointerCapture(event.pointerId)) {
      this.element.releasePointerCapture(event.pointerId);
    }
  }

  /** Screen pixels per scene unit, from the viewBox fitting the element. */
  private pixelsPerUnit(): number {
    const box = this.element.getBoundingClientRect();
    const width = this.element.viewBox.baseVal.width;
    return width > 0 && box.width > 0 ? box.width / width : 1;
  }

  /** The pointer in scene units, before any panning or zooming. */
  private toScene(event: MouseEvent): ScenePoint {
    const box = this.element.getBoundingClientRect();
    const unit = this.pixelsPerUnit();
    return { x: (event.clientX - box.left) / unit, y: (event.clientY - box.top) / unit };
  }

  private transform(): void {
    this.world.setAttribute("transform", `translate(${this.x} ${this.y}) scale(${this.scale})`);
  }

  private static build(element: SceneElement): SVGElement {
    let node: SVGElement;
    switch (element.type) {
      case "line":
        node = SvgView.node("line", { x1: element.from.x, y1: element.from.y, x2: element.to.x, y2: element.to.y });
        break;
      case "circle":
        node = SvgView.node("circle", { cx: element.centre.x, cy: element.centre.y, r: element.radius });
        break;
      case "rect":
        node = SvgView.node("rect", { x: element.corner.x, y: element.corner.y, width: element.width, height: element.height });
        break;
      case "polygon":
        node = SvgView.node("polygon", { points: element.points.map((point) => `${point.x},${point.y}`).join(" ") });
        break;
      case "text":
        node = SvgView.node("text", { x: element.at.x, y: element.at.y });
        for (const run of element.runs) {
          if (run.attributes) {
            const span = document.createElementNS(NS, "tspan");
            SvgView.apply(span, run.attributes);
            span.textContent = run.text;
            node.append(span);
          } else {
            node.append(document.createTextNode(run.text));
          }
        }
        break;
      case "group":
        node = SvgView.node("g", {});
        node.append(...element.children.map((child) => SvgView.build(child)));
        break;
      case "title":
      case "description":
        node = SvgView.node(element.type === "title" ? "title" : "desc", {});
        node.textContent = element.text;
        break;
    }
    SvgView.apply(node, element.attributes);
    return node;
  }

  private static node(tag: string, geometry: Record<string, number | string>): SVGElement {
    const node = document.createElementNS(NS, tag);
    for (const [name, value] of Object.entries(geometry)) {
      node.setAttribute(name, String(value));
    }
    return node;
  }

  /** Sets attributes, skipping any that could run script (event handlers, links). */
  private static apply(node: Element, attributes: SceneAttributes): void {
    for (const [name, value] of Object.entries(attributes)) {
      const lower = name.toLowerCase();
      if (lower.startsWith("on") || lower === "href" || lower.endsWith(":href")) {
        continue;
      }
      node.setAttribute(name, Array.isArray(value) ? value.join(",") : String(value));
    }
  }
}
