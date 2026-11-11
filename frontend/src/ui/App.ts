import { ChemistryApi } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import { ResultsView } from "./ResultsView";
import { SmilesForm } from "./SmilesForm";

export class App {
  private readonly form = new SmilesForm((smiles) => void this.render(smiles));
  private readonly results = new ResultsView();
  /** Ignores a response if a newer render has started since. */
  private latest = 0;

  constructor(private readonly api: ChemistryApi) {}

  mount(root: HTMLElement): void {
    const title = document.createElement("h1");
    title.textContent = "Chemistry";

    root.replaceChildren(title, this.form.element, this.results.element);
  }

  private async render(smiles: string): Promise<void> {
    if (smiles === "") {
      this.results.showError("Enter a SMILES string first.");
      return;
    }

    const request = ++this.latest;
    this.form.setBusy(true);
    this.results.showMessage("Rendering…");

    try {
      const svg = await this.api.render(smiles);
      if (request === this.latest) {
        this.results.showSvg(svg);
      }
    } catch (error) {
      if (request === this.latest) {
        this.results.showError(error instanceof ApiError ? error.message : String(error));
      }
      console.error("Render failed", error);
    } finally {
      if (request === this.latest) {
        this.form.setBusy(false);
      }
    }
  }
}
