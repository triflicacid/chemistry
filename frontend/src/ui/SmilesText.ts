import type { Span } from "../api/types";

export class SmilesText {
  readonly element = document.createElement("code");

  constructor(
    private readonly text: string,
    className = "smiles",
  ) {
    this.element.className = className;
    this.highlight([]);
  }

  /** Highlights exactly the given none-overlapping ranges. */
  highlight(spans: readonly (Span | null)[]): void {
    const ordered = spans.filter((span): span is Span => span !== null).sort((a, b) => a.start - b.start);
    const nodes: Node[] = [];
    let position = 0;
    for (const span of ordered) {
      if (span.start > position) {
        nodes.push(document.createTextNode(this.text.slice(position, span.start)));
      }
      const highlighted = document.createElement("span");
      highlighted.className = "smiles-highlight";
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
