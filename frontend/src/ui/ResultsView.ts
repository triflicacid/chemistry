import type { MoleculeId } from "../api/types";
import { sanitiseSvg } from "../svg";

/** The section below the form where results or an error are shown. */
export class ResultsView {
  readonly element = document.createElement("section");

  constructor() {
    this.element.className = "results";
  }

  showMessage(text: string): void {
    this.show(text, "message");
  }

  showError(text: string): void {
    this.show(text, "message error");
  }

  /** Shows a rendered molecule, replacing whatever was there. */
  showSvg(svg: string, moleculeId: MoleculeId): void {
    const card = document.createElement("figure");
    card.className = "card";
    card.dataset.molecule = moleculeId;
    card.append(sanitiseSvg(svg));
    this.element.replaceChildren(card);
  }

  private show(text: string, className: string): void {
    const message = document.createElement("p");
    message.className = className;
    message.textContent = text;
    this.element.replaceChildren(message);
  }
}
