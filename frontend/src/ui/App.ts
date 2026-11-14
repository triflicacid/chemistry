import type { ChemistryApi } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import { ViewStore, type PanelState } from "../store";
import { LibraryDialog } from "./LibraryDialog";
import { moleculeTab, queryTab } from "./LibraryTabs";
import { MoleculePanel, type Mode, type PanelHost } from "./MoleculePanel";
import { OptionsPanel } from "./OptionsPanel";
import { SearchForm } from "./SearchForm";

/** 2D options that only show in a projection of a 3D structure, so changing one redraws nothing. */
const PROJECTION_ONLY_2D = new Set(["depth_bonds"]);

export class App implements PanelHost {
  private readonly store = new ViewStore();
  private readonly form = new SearchForm((smarts) => this.search(smarts));
  private readonly results = document.createElement("section");
  private readonly add = document.createElement("button");
  private readonly library: LibraryDialog;
  private readonly optionsSlot = document.createElement("div");
  private panels: MoleculePanel[] = [];
  private readonly matchErrors = new Map<MoleculePanel, string>();
  private mode: Mode = this.store.panels.at(-1)?.mode ?? "2d";
  private panel2d: OptionsPanel | null = null;
  private panel3d: OptionsPanel | null = null;
  private panelMatch: OptionsPanel | null = null;
  private smartsText = "";
  private renderTimer: number | undefined;
  /** The 2D options the drawings on screen were made with, so that a change making no difference to them is ignored. */
  private rendered2d = "";
  private matchTimer: number | undefined;
  /** True while stored molecules are loading, when the panels do not yet hold them all. */
  private restoring = false;

  constructor(readonly api: ChemistryApi) {
    this.library = new LibraryDialog([
      moleculeTab(api, (smiles) => this.insertSmiles(smiles)),
      queryTab(api, (smarts) => this.applySmarts(smarts), (smiles) => this.insertSmiles(smiles)),
    ]);
    this.results.className = "results";
    this.add.type = "button";
    this.add.className = "add-molecule";
    this.add.textContent = "+ Add molecule";
    this.add.addEventListener("click", () => this.addPanel().startEmpty());
  }

  options2d(): OptionsPanel | null {
    return this.panel2d;
  }

  options3d(): OptionsPanel | null {
    return this.panel3d;
  }

  optionsMatch(): OptionsPanel | null {
    return this.panelMatch;
  }

  smarts(): string {
    return this.smartsText;
  }

  modeChosen(mode: Mode): void {
    this.mode = mode;
    this.persist();
  }

  moleculeChanged(): void {
    this.persist();
  }

  matchFailed(panel: MoleculePanel, message: string | null): void {
    if (message === null) {
      this.matchErrors.delete(panel);
    } else {
      this.matchErrors.set(panel, message);
    }
    this.form.setProblem(this.matchErrors.values().next().value ?? null);
  }

  removeRequested(panel: MoleculePanel): void {
    this.matchErrors.delete(panel);
    this.form.setProblem(this.matchErrors.values().next().value ?? null);
    this.panels = this.panels.filter((candidate) => candidate !== panel);
    panel.dispose();
    this.persist();
  }

  mount(root: HTMLElement): void {
    root.append(this.form.element, this.optionsSlot, this.results, this.add, this.library.element);
    const libraryButton = document.createElement("button");
    libraryButton.type = "button";
    libraryButton.className = "small";
    libraryButton.textContent = "Library";
    libraryButton.addEventListener("click", () => this.library.open());
    this.form.addAction(libraryButton);
    void this.loadOptions();

    this.smartsText = this.store.smarts;
    this.form.setSmarts(this.smartsText);
    const stored = this.store.panels;
    if (stored.length === 0) {
      this.addPanel().startEmpty();
    }
    this.restoring = stored.length > 0;
    const loading = stored.map((state) => this.addPanel(state.mode).restore(state));
    void Promise.all(loading).finally(() => {
      this.restoring = false;
      this.persist();
    });
  }

  /** Adds a panel showing this SMILES. */
  private insertSmiles(smiles: string): Promise<void> {
    this.panels.filter((panel) => panel.blank).forEach((panel) => this.removeRequested(panel));
    return this.addPanel().load(smiles);
  }

  /** @param mode the drawing it opens on, the one last switched to by default */
  private addPanel(mode: Mode = this.mode): MoleculePanel {
    const panel = new MoleculePanel(this, mode);
    this.panels.push(panel);
    this.results.append(panel.element);
    return panel;
  }

  /** Remembers the molecules shown and how each is drawn, so that they return when the page is reloaded. */
  private persist(): void {
    if (this.restoring) {
      return;
    }
    const states = this.panels.map((panel) => panel.stored).filter((state): state is PanelState => state !== null);
    this.store.rememberPanels(states);
  }

  /** Searches every molecule shown for a pattern taken from the library. */
  private applySmarts(smarts: string): void {
    this.form.setSmarts(smarts);
    this.search(smarts);
    this.library.element.close();
  }

  private search(smarts: string): void {
    this.smartsText = smarts;
    this.store.rememberSmarts(smarts);
    this.panels.forEach((panel) => void panel.updateMatches());
  }

  private async loadOptions(): Promise<void> {
    try {
      const [descriptors2d, descriptors3d, descriptorsMatch] = await Promise.all([
        this.api.options2d(),
        this.api.options3d(),
        this.api.optionsMatch(),
      ]);
      this.panel2d = new OptionsPanel(descriptors2d);
      this.panel3d = new OptionsPanel(descriptors3d);
      this.panelMatch = new OptionsPanel(descriptorsMatch);
      this.rendered2d = App.signature(this.panel2d);
      this.panel2d.onChange = () => this.render2dChanged();
      this.panel3d.onChange = () => this.panels.forEach((panel) => void panel.updateThree());
      this.panelMatch.onChange = () => this.matchOptionsChanged();
      this.panel2d.toggle.textContent = "2D options";
      this.panel3d.toggle.textContent = "3D options";
      this.panelMatch.toggle.textContent = "Match options";
      this.form.addAction(this.panel2d.toggle);
      this.form.addAction(this.panel3d.toggle);
      this.form.addAction(this.panelMatch.toggle);
      this.optionsSlot.replaceChildren(this.panel2d.element, this.panel3d.element, this.panelMatch.element);
      this.panels.forEach((panel) => void panel.updateMatches());
    } catch (error) {
      this.optionsSlot.textContent = `Options unavailable: ${error instanceof ApiError ? error.message : String(error)}`;
    }
  }

  /** Redraws the 2D drawings once the user stops changing the options, as only the server can draw them. */
  private render2dChanged(): void {
    const signature = App.signature(this.panel2d);
    if (signature === this.rendered2d) {
      return;
    }
    this.rendered2d = signature;
    window.clearTimeout(this.renderTimer);
    this.renderTimer = window.setTimeout(() => this.panels.forEach((panel) => void panel.updateTwo()), 250);
  }

  /** Signature of 2d options so we can detect a change */
  private static signature(options: OptionsPanel | null): string {
    const values = Object.entries(options?.resolved() ?? {}).filter(([name]) => !PROJECTION_ONLY_2D.has(name));
    return JSON.stringify(values);
  }

  /** Redraws the matches once the user stops changing the options. */
  private matchOptionsChanged(): void {
    window.clearTimeout(this.matchTimer);
    this.matchTimer = window.setTimeout(() => this.panels.forEach((panel) => void panel.updateMatches()), 250);
  }
}
