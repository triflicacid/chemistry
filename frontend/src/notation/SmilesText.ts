import "./notation.css";
import type {Span} from "../api/types/span";

/** A range to highlight, and which of the stylesheet's colours to give it. */
export interface Highlight {
  span: Span | null;
  colour: number;
}

/** What reacts to the pointer on the text, each told the index of the character it is on. */
export interface TextHandlers {
  /** The pointer is over a character, or null when it is over none. */
  hover: (offset: number | null) => void;
  pick: (offset: number) => void;
}

/** SMILES text that the pointer can mark and click, which may be one part of a longer text. */
export class SmilesText {
  /** How many highlight colours the stylesheet defines (.smiles-highlight-0 ...). */
  private static readonly COLOURS = 3;

  readonly element = document.createElement("code");

  /**
   * @param text the text shown
   * @param className the stylesheet class to give it
   * @param origin where `text` begins in the longer text it is part of, or 0 if it is the whole
   */
  constructor(
    private readonly text: string,
    className = "smiles",
    private readonly origin = 0,
  ) {
    this.element.className = className;
    this.highlight([]);
  }

  /** The text shown, without any highlighting. */
  get value(): string {
    return this.text;
  }

  /**
   * Makes the text respond to the pointer, which marks it as something to click.
   *
   * @param handlers told the character the pointer is over, or null when it is over none, and which one is clicked
   * @returns how to stop listening
   */
  listen(handlers: TextHandlers): () => void {
    const move = (event: PointerEvent) => {
      const offset = this.offsetAt(event.clientX, event.clientY);
      handlers.hover(offset === null ? null : offset + this.origin);
    };
    const leave = () => handlers.hover(null);
    const click = (event: MouseEvent) => {
      const offset = this.offsetAt(event.clientX, event.clientY);
      if (offset !== null) {
        handlers.pick(offset + this.origin);
      }
    };
    this.element.classList.add("pickable");
    this.element.addEventListener("pointermove", move);
    this.element.addEventListener("pointerleave", leave);
    this.element.addEventListener("click", click);
    return () => {
      this.element.classList.remove("pickable");
      this.element.removeEventListener("pointermove", move);
      this.element.removeEventListener("pointerleave", leave);
      this.element.removeEventListener("click", click);
    };
  }

  /** The index of the character at a point on screen, or null if the point is not over one. */
  private offsetAt(x: number, y: number): number | null {
    const position = document.caretPositionFromPoint?.(x, y);
    const range = position ? null : document.caretRangeFromPoint?.(x, y);
    const node = position?.offsetNode ?? range?.startContainer;
    const within = position?.offset ?? range?.startOffset;
    if (!node || within === undefined || !this.element.contains(node)) {
      return null;
    }

    // the caret sits between characters, so the one under the pointer is whichever side of it the point is on
    const walker = document.createTreeWalker(this.element, NodeFilter.SHOW_TEXT);
    let before = 0;
    for (let text = walker.nextNode(); text !== null && text !== node; text = walker.nextNode()) {
      before += text.textContent?.length ?? 0;
    }
    const caret = before + within;
    for (const offset of [caret - 1, caret]) {
      if (offset >= 0 && offset < this.text.length && this.covers(offset, x, y)) {
        return offset;
      }
    }
    return null;
  }

  /**
   * @param span a range in the longer text's offsets
   * @returns the part of it that is in this text, in this text's offsets, or null if none is
   */
  private within(span: Span | null): Span | null {
    if (span === null) {
      return null;
    }
    const start = Math.max(0, span.start - this.origin);
    const end = Math.min(this.text.length, span.end - this.origin);
    return end > start ? { start, end } : null;
  }

  private covers(offset: number, x: number, y: number): boolean {
    const walker = document.createTreeWalker(this.element, NodeFilter.SHOW_TEXT);
    let start = 0;
    for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) {
      const length = node.textContent?.length ?? 0;
      if (offset < start + length) {
        const range = document.createRange();
        range.setStart(node, offset - start);
        range.setEnd(node, offset - start + 1);
        const box = range.getBoundingClientRect();
        return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
      }
      start += length;
    }
    return false;
  }

  /**
   * Highlights exactly the given none-overlapping ranges, each in its own colour.
   * A colour cycles through the pallet, and a range of null is left out.
   */
  highlight(highlights: readonly Highlight[]): void {
    const ordered = highlights
      .map((entry) => ({ span: this.within(entry.span), colour: entry.colour }))
      .filter((entry): entry is { span: Span; colour: number } => entry.span !== null)
      .sort((a, b) => a.span.start - b.span.start);
    const nodes: Node[] = [];
    let position = 0;
    for (const { span, colour } of ordered) {
      if (span.start > position) {
        nodes.push(document.createTextNode(this.text.slice(position, span.start)));
      }
      const highlighted = document.createElement("span");
      highlighted.className = `smiles-highlight smiles-highlight-${colour % SmilesText.COLOURS}`;
      highlighted.textContent = this.text.slice(span.start, span.end);
      nodes.push(highlighted);
      position = span.end;
    }
    if (position < this.text.length) {
      nodes.push(document.createTextNode(this.text.slice(position)));
    }
    this.element.replaceChildren(...nodes);
  }
}
