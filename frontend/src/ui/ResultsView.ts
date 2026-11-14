import type { MoleculeId, MoleculeMetadata } from "../api/types";
import { Selection } from "../store";
import type { MoleculeView } from "../view/MoleculeView";
import { FormulaView } from "./FormulaView";
import { MoleculeInspector } from "./MoleculeInspector";
import { SmilesField } from "./SmilesField";
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

/** The section below the form where results or an error are shown. Views passed in are never disposed here. */
export class ResultsView {
  readonly element = document.createElement("section");
  private shown: Shown | null = null;
  private inspector: MoleculeInspector | null = null;
  private formula: FormulaView | null = null;
  private readonly selection = new Selection();

  /** @param edit applies a SMILES the user typed in place of the one shown; resolves to why it was refused, or null */
  constructor(private readonly edit: (smiles: string) => Promise<string | null>) {
    this.element.className = "results";
  }

  showMessage(text: string): void {
    this.show(text, "message", "p");
  }

  showError(text: string): void {
    this.show(text, "message error", "pre");
  }

  /**
   * Shows a SMILES being entered, because there is no molecule to show.
   *
   * @param smiles what was entered, or blank for a new panel
   * @param problem why it cannot be shown, if it was tried
   */
  showEntry(smiles: string, problem: string | null): void {
    this.reset();
    const field = new SmilesField(new SmilesText(smiles), this.edit, true);
    field.setError(problem);
    const card = document.createElement("figure");
    card.className = "card";
    card.append(ResultsView.label("SMILES"), field.element);
    this.element.replaceChildren(card);
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
    card.append(
      ResultsView.label("SMILES"),
      new SmilesField(text, this.edit).element,
      ResultsView.label("Canonical SMILES"),
      new SmilesField(canonical, this.edit).element,
      view.element,
      ResultsView.downloadButton(view, canonicalSmiles),
    );
    this.element.replaceChildren(card);
    this.shown = { moleculeId, card, view, smiles: text, canonical, unsubscribe };
  }

  /** Adds a line of text to the card being shown. */
  note(text: string): void {
    const note = document.createElement("p");
    note.className = "message";
    note.textContent = text;
    this.shown?.card.append(note);
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

  private static downloadButton(view: MoleculeView, name: string): HTMLElement {
    const row = document.createElement("div");
    row.className = "download-row";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "small";
    button.textContent = `Download ${view.imageExtension.toUpperCase()}`;
    button.addEventListener("click", () => {
      void view.exportImage().then((blob) => {
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = `${name.replace(/[^\w.+-]+/g, "_").slice(0, 60) || "molecule"}.${view.imageExtension}`;
        link.click();
        URL.revokeObjectURL(link.href);
      }).catch((error) => console.warn("Could not download the image", error));
    });
    row.append(button);
    return row;
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
    if (this.shown) {
      // the caller owns the view and may show it again, so leave it clean rather than disposing it
      this.shown.unsubscribe();
      this.shown.view.setSelection({ atoms: new Set(), bonds: new Set() });
      this.shown.view.setHighlights({ hover: null, ends: [] });
    }
    this.shown = null;
  }
}
