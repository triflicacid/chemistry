import type { Span } from "../api/types";

/** A range to highlight, and which of the stylesheet's colours to give it. */
export interface Highlight {
  span: Span | null;
  colour: number;
}

export class SmilesText {
  /** How many highlight colours the stylesheet defines (.smiles-highlight-0 ...). */
  private static readonly COLOURS = 3;

  readonly element = document.createElement("code");

  constructor(
    private readonly text: string,
    className = "smiles",
  ) {
    this.element.className = className;
    this.highlight([]);
  }

  /** The text shown, without any highlighting. */
  get value(): string {
    return this.text;
  }

  /**
   * Highlights exactly the given none-overlapping ranges, each in its own colour.
   * A colour cycles through the pallet, and a range of null is left out.
   */
  highlight(highlights: readonly Highlight[]): void {
    const ordered = highlights
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
