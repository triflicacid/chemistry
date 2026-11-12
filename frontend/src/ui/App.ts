import { ChemistryApi } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import type { MoleculeId } from "../api/types";
import { ViewStore } from "../store";
import { OptionsPanel } from "./OptionsPanel";
import { ResultsView } from "./ResultsView";
import { SmilesForm } from "./SmilesForm";

/** The whole page: title, SMILES form, options, results. */
export class App {
  private readonly results = new ResultsView();
  private readonly store = new ViewStore();
  private readonly form = new SmilesForm((smiles) => void this.render(smiles));
  private options: OptionsPanel | null = null;
  private readonly optionsSlot = document.createElement("div");

  constructor(private readonly api: ChemistryApi) {}

  mount(root: HTMLElement): void {
    const title = document.createElement("h1");
    title.textContent = "Chemistry";

    this.results.showMessage("Enter a SMILES string and press RENDER.");
    root.append(title, this.form.element, this.optionsSlot, this.results.element);
    void this.loadOptions();
  }

  private async loadOptions(): Promise<void> {
    try {
      this.options = new OptionsPanel(await this.api.options());
      this.form.addAction(this.options.toggle);
      this.optionsSlot.replaceChildren(this.options.element);
    } catch (error) {
      this.optionsSlot.textContent = `Options unavailable: ${App.describe(error)}`;
    }
  }

  private async render(smiles: string): Promise<void> {
    if (!smiles) {
      this.results.showError("Please enter a SMILES string.");
      return;
    }

    this.form.setBusy(true);
    try {
      const created = await this.api.createMolecule(smiles);
      const { svg, moleculeId } = await this.api.renderMolecule(created.id, this.options?.values() ?? {});
      this.store.viewMolecule(moleculeId);
      this.results.showMolecule(svg, moleculeId, created.smiles, created.canonical_smiles);
      void this.inspect(moleculeId);
    } catch (error) {
      this.store.clear();
      this.results.showError(App.describe(error));
    } finally {
      this.form.setBusy(false);
    }
  }

  /** Fetches what hovering needs; the drawing is usable without it, so failing is not an error to show. */
  private async inspect(moleculeId: MoleculeId): Promise<void> {
    try {
      const metadata = await this.api.metadata(moleculeId);
      if (this.store.moleculeId === moleculeId) {
        this.results.inspect(metadata, moleculeId);
      }
    } catch (error) {
      console.warn("Could not load the molecule's metadata", error);
    }
  }

  private static describe(error: unknown): string {
    return error instanceof ApiError ? error.message : String(error);
  }
}
