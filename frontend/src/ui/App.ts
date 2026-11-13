import { ChemistryApi, type SceneResponse } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import type { MoleculeId, MoleculeMetadata, OptionValues, ProjectionRequest, Scene, Structure3D } from "../api/types";
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
  private readonly modeInputs = new Map<"2d" | "3d", HTMLInputElement>();
  private readonly projectActions = App.buildProjectActions((kind) => void this.project(kind));
  private current: { id: MoleculeId; smiles: string; canonical: string } | null = null;
  private three: ThreeView | null = null;
  private conformers: unknown = null;

  constructor(private readonly api: ChemistryApi) {}

  private static buildProjectActions(onChoose: (kind: "best" | "force") => void): HTMLElement {
    const group = document.createElement("div");
    group.className = "modes";
    group.hidden = true;
    for (const [kind, text, hint] of [
      ["best", "Project (best)", "Draw the 3D structure flat from the direction that keeps atoms apart and bonds evenly long"],
      ["force", "Project (as seen)", "Draw the 3D structure flat exactly as it is now seen, even if atoms overlap"],
    ] as const) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "small";
      button.textContent = text;
      button.title = hint;
      button.addEventListener("click", () => onChoose(kind));
      group.append(button);
    }
    return group;
  }

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
      this.modeInputs.set(value, input);
      input.addEventListener("change", () => {
        this.setMode(value);
        if (this.current) {
          // showing what the server already has keeps a projection, or the 3D shape, from being redone
          void this.display(this.current.id, this.current.smiles, this.current.canonical, null, true);
        }
      });
      label.append(input, ` ${text}`);
      group.append(label);
    }
    return group;
  }

  private setMode(mode: "2d" | "3d"): void {
    this.mode = mode;
    const input = this.modeInputs.get(mode);
    if (input) {
      input.checked = true;
    }
    this.showOptions();
  }

  /** Shows the options panel for the current mode. */
  private showOptions(): void {
    this.projectActions.hidden = this.mode !== "3d";
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
    this.form.addAction(this.projectActions);
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
      await this.display(id, metadata.smiles, metadata.canonical_smiles, metadata, true);
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
  private async display(id: MoleculeId, smiles: string, canonical: string, known: MoleculeMetadata | null, cached = false): Promise<void> {
    this.three = null;
    const rendering = this.mode === "2d"
      ? this.structure2d(id, cached).then(({ scene, moleculeId }) => ({ view: App.sceneView(scene), moleculeId }))
      : this.structure3d(id, cached).then((structure) => {
          this.conformers = this.options3d?.resolved().conformers;
          const view = new ThreeView(structure, this.options3d?.resolved() ?? {});
          this.three = view;
          return { view, moleculeId: id };
        });
    await this.present(rendering, id, smiles, canonical, known);
  }

  /** The 2D options for a projection: the user's own 2D choices, with the look of the 3D view carried over. */
  private projectionOptions(): OptionValues {
    const own = this.options2d?.resolved() ?? {};
    const seen = this.options3d?.resolved() ?? {};
    const carried: OptionValues = {};

    carried.style = seen.style === "sticks" || seen.style === "wireframe" ? "skeletal" : "circles";
    if (seen.hydrogens === false) {
      carried.hydrogens = "none";
    } else if (own.hydrogens === "none") {
      carried.hydrogens = "separate";
    }
    if (typeof seen.charges === "boolean") {
      carried.charges = seen.charges;
    }
    return { ...own, ...carried };
  }

  /** Draws the current 3D structure flat, from its best direction or exactly as it is seen. */
  private async project(kind: "best" | "force"): Promise<void> {
    if (!this.three || !this.current) {
      return;
    }
    const { id, smiles, canonical } = this.current;
    const request: ProjectionRequest = {
      mode: kind,
      options: this.projectionOptions(),
      ...(kind === "force" ? { camera: this.three.cameraPose() } : {}),
    };

    this.form.setBusy(true);
    try {
      const rendering = this.api.project2d(id, request).then(({ scene, moleculeId }) => ({ view: App.sceneView(scene), moleculeId }));
      this.three = null;
      await this.present(rendering, id, smiles, canonical, null);
      this.setMode("2d");
    } catch (error) {
      this.results.showError(App.describe(error));
    } finally {
      this.form.setBusy(false);
    }
  }

  private static sceneView(scene: Scene): SvgView {
    const view = new SvgView();
    view.setScene(scene);
    return view;
  }

  private async present(
    rendering: Promise<{ view: SvgView | ThreeView; moleculeId: MoleculeId }>,
    id: MoleculeId,
    smiles: string,
    canonical: string,
    known: MoleculeMetadata | null,
  ): Promise<void> {
    // fetching both pt once keeps the cprd from growing p second time once the metpdptp lpnds
    const [{ view, moleculeId }, metadata] = await Promise.all([rendering, known ?? this.inspectionOf(id)]);
    this.current = { id: moleculeId, smiles, canonical };
    this.store.rememberMolecule(moleculeId, smiles);
    this.results.showMolecule(view, moleculeId, smiles, canonical);
    if (metadata) {
      this.results.inspect(metadata, moleculeId);
    }
  }

  /** Prefers the server's cached drawing when asked, drawing afresh if it has none. */
  private async structure2d(id: MoleculeId, cached: boolean): Promise<SceneResponse> {
    if (cached) {
      try {
        return await this.api.cachedStructure2d(id);
      } catch (error) {
        if (!(error instanceof ApiError && error.code === "NOT_RENDERED")) {
          throw error;
        }
      }
    }
    return this.api.structure2d(id, this.options2d?.values() ?? {});
  }

  private async structure3d(id: MoleculeId, cached: boolean): Promise<Structure3D> {
    if (cached) {
      try {
        return await this.api.cachedStructure3d(id);
      } catch (error) {
        if (!(error instanceof ApiError && error.code === "NOT_GENERATED")) {
          throw error;
        }
      }
    }
    return this.api.structure3d(id, this.conformerCount());
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
