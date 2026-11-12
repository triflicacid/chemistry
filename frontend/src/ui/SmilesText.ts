import type { Span } from "../api/types";

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

  /**
   * Highlights exactly the given none-overlapping ranges.
   * The nth range gets colour {@code firstColour} + n (index, cycles through pallet)
   */
  highlight(spans: readonly (Span | null)[], firstColour = 0): void {
    const ordered = spans
      .map((span, index) => ({ span, colour: firstColour + index }))
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
