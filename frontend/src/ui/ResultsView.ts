import type { MoleculeId, MoleculeMetadata } from "../api/types";
import { sanitiseSvg } from "../svg";
import { MoleculeInspector } from "./MoleculeInspector";
import { SmilesText } from "./SmilesText";

/** The molecule currently shown. */
interface Shown {
  moleculeId: MoleculeId;
  svg: SVGSVGElement;
  smiles: SmilesText;
}

/** The section below the form where results or an error are shown. */
export class ResultsView {
  readonly element = document.createElement("section");
  private shown: Shown | null = null;
  private inspector: MoleculeInspector | null = null;

  constructor() {
    this.element.className = "results";
  }

  showMessage(text: string): void {
    this.show(text, "message");
  }

  showError(text: string): void {
    this.show(text, "message error");
  }

  /** Shows a rendered molecule and its SMILES, replacing whatever was there. */
  showMolecule(svg: string, moleculeId: MoleculeId, smiles: string): void {
    this.reset();

    const drawing = sanitiseSvg(svg);
    const text = new SmilesText(smiles);
    const card = document.createElement("figure");
    card.className = "card";
    card.dataset.molecule = moleculeId;
    card.append(text.element, drawing);
    this.element.replaceChildren(card);
    this.shown = { moleculeId, svg: drawing, smiles: text };
  }

  /** Makes the molecule shown inspectable, unless it is no longer the one being shown. */
  inspect(metadata: MoleculeMetadata, moleculeId: MoleculeId): void {
    if (this.shown?.moleculeId !== moleculeId) {
      return;
    }
    this.inspector?.detach();
    this.inspector = new MoleculeInspector(this.shown.svg, this.shown.smiles, metadata);
  }

  private show(text: string, className: string): void {
    this.reset();
    const message = document.createElement("p");
    message.className = className;
    message.textContent = text;
    this.element.replaceChildren(message);
  }

  private reset(): void {
    this.inspector?.detach();
    this.inspector = null;
    this.shown = null;
  }
}
