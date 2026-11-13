import type { ChemistryApi, SceneResponse } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import type { MoleculeId, MoleculeMetadata, OptionValues, ProjectionRequest, Scene, Structure3D } from "../api/types";
import { SvgView } from "../view/SvgView";
import { ThreeView } from "../view/ThreeView";
import type { OptionsPanel } from "./OptionsPanel";
import { ResultsView } from "./ResultsView";

export type Mode = "2d" | "3d";

/** What a panel needs from the page around it. */
export interface PanelHost {
  readonly api: ChemistryApi;
  options2d(): OptionsPanel | null;
  options3d(): OptionsPanel | null;
  modeChosen(mode: Mode): void;
}

interface Cache {
  id: MoleculeId;
  two?: SvgView;
  three?: ThreeView;
  conformers?: unknown;
}

let panelCount = 0;

export class MoleculePanel {
  readonly element = document.createElement("div");
  private readonly results = new ResultsView();
  private readonly projectActions: HTMLElement;
  /** The mode choice and projection buttons, shown once there is a molecule to act on. */
  private readonly toolbar = document.createElement("div");
  private readonly modeInputs = new Map<Mode, HTMLInputElement>();
  private cache: Cache | null = null;
  private current: { id: MoleculeId; smiles: string; canonical: string } | null = null;
  private three: ThreeView | null = null;
  private conformers: unknown = null;

  constructor(private readonly host: PanelHost, private mode: Mode) {
    this.element.className = "panel";
    this.projectActions = this.buildProjectActions();
    this.projectActions.hidden = mode !== "3d";
    this.toolbar.className = "actions";
    this.toolbar.hidden = true;
    this.toolbar.append(this.buildModes(), this.projectActions);
    this.element.append(this.toolbar, this.results.element);
  }

  private buildModes(): HTMLElement {
    const name = `mode-${panelCount++}`;
    const group = document.createElement("div");
    group.className = "radios modes";
    group.setAttribute("role", "radiogroup");
    for (const [value, text] of [["2d", "2D"], ["3d", "3D"]] as const) {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "radio";
      input.name = name;
      input.value = value;
      input.checked = value === this.mode;
      this.modeInputs.set(value, input);
      input.addEventListener("change", () => {
        this.host.modeChosen(value);
        void this.setMode(value);
      });
      label.append(input, ` ${text}`);
      group.append(label);
    }
    return group;
  }

  /** Creates the molecule on the server and draws it; a failure is shown in the panel rather than thrown. */
  async load(smiles: string): Promise<void> {
    this.results.showMessage(`Rendering ${smiles}...`);
    try {
      const created = await this.host.api.createMolecule(smiles);
      await this.display(created.id, created.smiles, created.canonical_smiles, null);
      this.toolbar.hidden = false;
    } catch (error) {
      this.toolbar.hidden = true;
      this.results.showError(`${smiles}\n${MoleculePanel.describe(error)}`);
    }
  }

  /** Switches between the 2D and 3D drawing, reusing a view already made. */
  async setMode(mode: Mode): Promise<void> {
    this.mode = mode;
    this.modeInputs.get(mode)!.checked = true;
    this.projectActions.hidden = mode !== "3d";
    if (!this.current) {
      return;
    }
    try {
      await this.display(this.current.id, this.current.smiles, this.current.canonical, null, true, true);
    } catch (error) {
      this.results.showError(MoleculePanel.describe(error));
    }
  }

  /** Applies changed 3D options; only the number of conformers needs the server. */
  async updateThree(): Promise<void> {
    const options = this.host.options3d();
    if (!this.three || !this.current || !options) {
      return;
    }
    if (options.resolved().conformers !== this.conformers) {
      const { id, smiles, canonical } = this.current;
      try {
        await this.display(id, smiles, canonical, null);
      } catch (error) {
        this.results.showError(MoleculePanel.describe(error));
      }
      return;
    }
    this.three.setOptions(options.resolved());
  }

  dispose(): void {
    this.dropCache();
    this.element.remove();
  }

  private buildProjectActions(): HTMLElement {
    const group = document.createElement("div");
    group.className = "modes";
    for (const [kind, text, hint] of [
      ["best", "Project (best)", "Draw the 3D structure flat from the direction that keeps atoms apart and bonds evenly long"],
      ["force", "Project (as seen)", "Draw the 3D structure flat exactly as it is now seen, preserving its layout, even if atoms overlap"],
    ] as const) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "small";
      button.textContent = text;
      button.title = hint;
      button.addEventListener("click", () => void this.project(kind));
      group.append(button);
    }
    return group;
  }

  /** Draws the molecule in the current mode; with `reuse`, shows the view already made for this mode instead of fetching again. */
  private async display(
    id: MoleculeId,
    smiles: string,
    canonical: string,
    known: MoleculeMetadata | null,
    cached = false,
    reuse = false,
  ): Promise<void> {
    this.three = null;
    const kept = reuse && this.cache?.id === id ? this.cache : null;
    let rendering: Promise<{ view: SvgView | ThreeView; moleculeId: MoleculeId }>;
    if (this.mode === "2d") {
      rendering = kept?.two
        ? Promise.resolve({ view: kept.two, moleculeId: id })
        : this.structure2d(id, cached).then(({ scene, moleculeId }) => {
            const view = MoleculePanel.sceneView(scene);
            this.keep(moleculeId, "two", view);
            return { view, moleculeId };
          });
    } else if (kept?.three) {
      this.conformers = kept.conformers;
      this.three = kept.three;
      rendering = Promise.resolve({ view: kept.three, moleculeId: id });
    } else {
      rendering = this.structure3d(id, cached).then((structure) => {
        const resolved = this.host.options3d()?.resolved() ?? {};
        const view = new ThreeView(structure, resolved);
        this.conformers = resolved.conformers;
        this.keep(id, "three", view, resolved.conformers);
        this.three = view;
        return { view, moleculeId: id };
      });
    }
    await this.present(rendering, id, smiles, canonical, known);
  }

  /** Remembers a freshly made view, disposing the one it replaces and everything of a different molecule. */
  private keep(id: MoleculeId, mode: "two" | "three", view: SvgView | ThreeView, conformers?: unknown): void {
    if (this.cache?.id !== id) {
      this.dropCache();
      this.cache = { id };
    }
    const entry = this.cache as Cache;
    const old = entry[mode];
    if (old && old !== view) {
      this.results.showMessage("Drawing...");
      old.dispose();
    }
    if (mode === "two") {
      entry.two = view as SvgView;
    } else {
      entry.three = view as ThreeView;
      entry.conformers = conformers;
    }
  }

  private dropCache(): void {
    if (this.cache) {
      // lets the results view clean up the views before they are disposed
      this.results.showMessage("Drawing...");
    }
    this.cache?.two?.dispose();
    this.cache?.three?.dispose();
    this.cache = null;
  }

  /** The 2D options for a projection: the user's own 2D choices, with the look of the 3D view carried over. */
  private projectionOptions(): OptionValues {
    const own = this.host.options2d()?.resolved() ?? {};
    const seen = this.host.options3d()?.resolved() ?? {};
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
    const atoms = this.three.visibleAtoms();
    if (atoms.length === 0) {
      this.results.showError("Nothing is in view to project");
      return;
    }
    const request: ProjectionRequest = {
      mode: kind,
      atoms,
      options: this.projectionOptions(),
      ...(kind === "force" ? { camera: this.three.cameraPose() } : {}),
    };

    try {
      const rendering = this.host.api.project2d(id, request).then(({ scene, moleculeId }) => {
        const view = MoleculePanel.sceneView(scene);
        this.keep(moleculeId, "two", view);
        return { view, moleculeId };
      });
      this.three = null;
      await this.present(rendering, id, smiles, canonical, null);
      this.mode = "2d";
      this.modeInputs.get("2d")!.checked = true;
      this.projectActions.hidden = true;
    } catch (error) {
      this.results.showError(MoleculePanel.describe(error));
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
    // fetching both at once keeps the card from growing a second time once the metadata lands
    const [{ view, moleculeId }, metadata] = await Promise.all([rendering, known ?? this.inspectionOf(id)]);
    this.current = { id: moleculeId, smiles, canonical };
    this.results.showMolecule(view, moleculeId, smiles, canonical);
    if (metadata) {
      this.results.inspect(metadata, moleculeId);
    }
  }

  /** Prefers the server's cached drawing when asked, drawing afresh if it has none. */
  private async structure2d(id: MoleculeId, cached: boolean): Promise<SceneResponse> {
    if (cached) {
      try {
        return await this.host.api.cachedStructure2d(id);
      } catch (error) {
        if (!(error instanceof ApiError && error.code === "NOT_RENDERED")) {
          throw error;
        }
      }
    }
    return this.host.api.structure2d(id, this.host.options2d()?.values() ?? {});
  }

  private async structure3d(id: MoleculeId, cached: boolean): Promise<Structure3D> {
    if (cached) {
      try {
        return await this.host.api.cachedStructure3d(id);
      } catch (error) {
        if (!(error instanceof ApiError && error.code === "NOT_GENERATED")) {
          throw error;
        }
      }
    }
    const count = this.host.options3d()?.resolved().conformers;
    return this.host.api.structure3d(id, typeof count === "number" ? count : 1);
  }

  /** Fetches the formula and what hovering needs; the drawing is usable without them, so failing is not an error to show. */
  private async inspectionOf(moleculeId: MoleculeId): Promise<MoleculeMetadata | null> {
    try {
      return await this.host.api.metadata(moleculeId);
    } catch (error) {
      console.warn("Could not load the molecule's metadata", error);
      return null;
    }
  }

  private static describe(error: unknown): string {
    return error instanceof ApiError ? error.message : String(error);
  }
}
