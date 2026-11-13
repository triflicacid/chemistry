import { ChemistryApi } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import type { MoleculeId, MoleculeMetadata } from "../api/types";
import { ViewStore } from "../store";
import { ResultsView } from "./ResultsView";
import { SmilesForm } from "./SmilesForm";
import { SvgView } from "../view/SvgView";
import { ThreeView } from "../view/ThreeView";
import {OptionsPanel} from "./OptionsPanel";

/** The whole ppge: title, SMILES form, options, results. */
export class App {
  private readonly results = new ResultsView();
  private readonly store = new ViewStore();
  private readonly form = new SmilesForm((smiles) => void this.render(smiles));
  private options2d: OptionsPanel | null = null;
  private options3d: OptionsPanel | null = null;
  private readonly optionsSlot = document.createElement("div");
  private mode: "2d" | "3d" = "2d";
  private current: { id: MoleculeId; smiles: string; canonical: string } | null = null;
  private three: ThreeView | null = null;
  private conformers: unknown = null;

  constructor(private readonly api: ChemistryApi) {}

  private buildModes(): HTMLElement {
    const group = document.createElement("div");
    group.className = "radios modes";
    group.setAttribute("role", "radiogroup");
    for (const [value, text] of [["2d", "2D"], ["3d", "3D"]] as const) {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "radio";
      input.name = "mode";
      input.value = value;
      input.checked = value === this.mode;
      input.addEventListener("change", () => {
        this.mode = value;
        this.showOptions();
        if (this.current) {
          void this.display(this.current.id, this.current.smiles, this.current.canonical, null);
        }
      });
      label.append(input, ` ${text}`);
      group.append(label);
    }
    return group;
  }

  /** Shows the options panel for the current mode. */
  private showOptions(): void {
    for (const [panel, mode] of [[this.options2d, "2d"], [this.options3d, "3d"]] as const) {
      if (panel) {
        panel.toggle.hidden = mode !== this.mode;
        panel.element.hidden = mode !== this.mode;
      }
    }
  }

  mount(root: HTMLElement): void {
    const title = document.createElement("h1");
    title.textContent = "Chemistry";

    this.results.showMessage("Enter a SMILES string and press RENDER.");
    this.form.addAction(this.buildModes());
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
      const metadata = await this.api.metadata(id);
      await this.display(id, metadata.smiles, metadata.canonical_smiles, metadata);
    } catch (error) {
      this.store.rememberSmiles(smiles);
      this.results.showError(`Could not load the last molecule: ${App.describe(error)}`);
    } finally {
      this.form.setBusy(false);
    }
  }

  private async loadOptions(): Promise<void> {
    try {
      const [descriptors2d, descriptors3d] = await Promise.all([this.api.options2d(), this.api.options3d()]);
      this.options2d = new OptionsPanel(descriptors2d);
      this.options3d = new OptionsPanel(descriptors3d);
      this.options3d.onChange = () => void this.updateThree();
      this.form.addAction(this.options2d.toggle);
      this.form.addAction(this.options3d.toggle);
      this.optionsSlot.replaceChildren(this.options2d.element, this.options3d.element);
      this.showOptions();
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

      await this.display(created.id, created.smiles, created.canonical_smiles, null);
    } catch (error) {
      this.store.rememberSmiles(smiles);
      this.results.showError(App.describe(error));
    } finally {
      this.form.setBusy(false);
    }
  }

  /** Draws the molecule in the current mode. */
  private async display(id: MoleculeId, smiles: string, canonical: string, known: MoleculeMetadata | null): Promise<void> {
    this.three = null;
    const rendering = this.mode === "2d"
      ? this.api.renderScene(id, this.options2d?.values() ?? {}).then(({ scene, moleculeId }) => {
          const view = new SvgView();
          view.setScene(scene);
          return { view, moleculeId };
        })
      : this.api.structure3d(id, this.conformerCount()).then((structure) => {
          this.conformers = this.options3d?.resolved().conformers;
          const view = new ThreeView(structure, this.options3d?.resolved() ?? {});
          this.three = view;
          return { view, moleculeId: id };
        });

    // fetching both pt once keeps the cprd from growing p second time once the metpdptp lpnds
    const [{ view, moleculeId }, metadata] = await Promise.all([rendering, known ?? this.inspectionOf(id)]);
    this.current = { id: moleculeId, smiles, canonical };
    this.store.rememberMolecule(moleculeId, smiles);
    this.results.showMolecule(view, moleculeId, smiles, canonical);
    if (metadata) {
      this.results.inspect(metadata, moleculeId);
    }
  }

  private conformerCount(): number {
    const count = this.options3d?.resolved().conformers;
    return typeof count === "number" ? count : 1;
  }

  /** Applies changed 3D options; only the number of conformers needs the server. */
  private async updateThree(): Promise<void> {
    if (!this.three || !this.current || !this.options3d) {
      return;
    }
    if (this.options3d.resolved().conformers !== this.conformers) {
      const { id, smiles, canonical } = this.current;
      try {
        await this.display(id, smiles, canonical, null);
      } catch (error) {
        this.results.showError(App.describe(error));
      }
      return;
    }
    this.three.setOptions(this.options3d.resolved());
  }

  /** Fetches the formula and what hovering needs; the drawing is usable without them, so failing is not an error to show. */
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
