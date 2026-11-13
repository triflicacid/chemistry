import type { MoleculeId, MoleculeMetadata } from "../api/types";
import { Selection } from "../store";
import type { MoleculeView } from "../view/MoleculeView";
import { FormulaView } from "./FormulaView";
import { MoleculeInspector } from "./MoleculeInspector";
import { SmilesText } from "./SmilesText";

/** The molecule currently shown. */
interface Shown {
  moleculeId: MoleculeId;
  card: HTMLElement;
  view: MoleculeView;
  smiles: SmilesText;
  canonical: SmilesText;
  unsubscribe: () => void;
}

/** The section below the form where results or an error are shown. */
export class ResultsView {
  readonly element = document.createElement("section");
  private shown: Shown | null = null;
  private inspector: MoleculeInspector | null = null;
  private formula: FormulaView | null = null;
  private readonly selection = new Selection();

  constructor() {
    this.element.className = "results";
  }

  showMessage(text: string): void {
    this.show(text, "message", "p");
  }

  showError(text: string): void {
    this.show(text, "message error", "pre");
  }

  /** Shows a rendered molecule and its SMILES. */
  showMolecule(view: MoleculeView, moleculeId: MoleculeId, smiles: string, canonicalSmiles: string): void {
    this.reset();

    this.selection.clear();
    const unsubscribe = this.selection.subscribe(() => view.setSelection(this.selection));
    const text = new SmilesText(smiles);
    const canonical = new SmilesText(canonicalSmiles);
    const card = document.createElement("figure");
    card.className = "card";
    card.dataset.molecule = moleculeId;
    card.append(ResultsView.label("SMILES"), text.element, ResultsView.label("Canonical SMILES"),     canonical.element, view.element);
    this.element.replaceChildren(card);
        this.shown = { moleculeId, card, view, smiles: text, canonical, unsubscribe };
  }

  inspect(metadata: MoleculeMetadata, moleculeId: MoleculeId): void {
    if (this.shown?.moleculeId !== moleculeId) {
      return;
    }
    this.inspector?.detach();
    this.formula?.element.remove();

    this.formula = new FormulaView(metadata.formula, metadata.fragments.length);
    this.shown.card.insertBefore(this.formula.element,     this.shown.view.element);
        this.inspector = new MoleculeInspector(this.shown.view, this.selection, this.shown.smiles, this.shown.canonical, this.formula, metadata);
  }

  private static label(text: string): HTMLElement {
    const label = document.createElement("span");
    label.className = "smiles-label";
    label.textContent = text;
    return label;
  }

  private show(text: string, className: string, elementTag?: string): void {
    this.reset();
    const message = document.createElement(elementTag ?? "p");
    message.className = className;
    message.textContent = text;
    this.element.replaceChildren(message);
  }

  private reset(): void {
    this.inspector?.detach();
    this.inspector = null;
    this.formula = null;
    this.shown?.unsubscribe();
    this.shown?.view.dispose();
    this.shown = null;
  }
}
