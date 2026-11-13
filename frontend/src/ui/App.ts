import { ChemistryApi } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import type { MoleculeId, MoleculeMetadata } from "../api/types";
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

    const remembered = this.store.remembered;
    if (remembered) {
      this.form.setSmiles(remembered.smiles);
      if (remembered.id !== null) {
        void this.restore(remembered.id, remembered.smiles);
      }
    }
  }

  /** Shows the molecule left from the last visit. */
  private async restore(id: MoleculeId, smiles: string): Promise<void> {
    this.results.showMessage("Loading the last molecule...");
    this.form.setBusy(true);
    try {
      const [{ scene, moleculeId }, metadata] = await Promise.all([
        this.api.moleculeScene(id),
        this.api.metadata(id),
      ]);
      this.store.rememberMolecule(moleculeId, metadata.smiles);
      this.results.showMolecule(scene, moleculeId, metadata.smiles, metadata.canonical_smiles);
      this.results.inspect(metadata, moleculeId);
    } catch (error) {
      this.store.rememberSmiles(smiles);
      this.results.showError(`Could not load the last molecule: ${App.describe(error)}`);
    } finally {
      this.form.setBusy(false);
    }
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

      // fetching both at once keeps the card from growing a second time once the metadata lands
      const [{ scene, moleculeId }, metadata] = await Promise.all([
        this.api.renderScene(created.id, this.options?.values() ?? {}),
        this.inspectionOf(created.id),
      ]);
      this.store.rememberMolecule(moleculeId, created.smiles);
      this.results.showMolecule(scene, moleculeId, created.smiles, created.canonical_smiles);
      if (metadata) {
        this.results.inspect(metadata, moleculeId);
      }
    } catch (error) {
      this.store.rememberSmiles(smiles);
      this.results.showError(App.describe(error));
    } finally {
      this.form.setBusy(false);
    }
  }

  /** Fetches the formulae and what hovering needs; the drawing is usable without them, so failing is not an error to show. */
  private async inspectionOf(moleculeId: MoleculeId): Promise<MoleculeMetadata | null> {
    try {
      return await this.api.metadata(moleculeId);
    } catch (error) {
      console.warn("Could not load the molecule's metadata", error);
      return null;
    }
  }

  private static describe(error: unknown): string {
    return error instanceof ApiError ? error.message : String(error);
  }
}
