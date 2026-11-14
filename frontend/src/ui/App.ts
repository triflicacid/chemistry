import type { ChemistryApi } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import { ViewStore } from "../store";
import { LibraryDialog } from "./LibraryDialog";
import { MoleculePanel, type Mode, type PanelHost } from "./MoleculePanel";
import { OptionsPanel } from "./OptionsPanel";
import { SearchForm } from "./SearchForm";

export class App implements PanelHost {
  private readonly store = new ViewStore();
  private readonly form = new SearchForm((smarts) => this.search(smarts));
  private readonly results = document.createElement("section");
  private readonly add = document.createElement("button");
  private readonly library: LibraryDialog;
  private readonly optionsSlot = document.createElement("div");
  private panels: MoleculePanel[] = [];
  private readonly matchErrors = new Map<MoleculePanel, string>();
  private mode: Mode = this.store.mode;
  private panel2d: OptionsPanel | null = null;
  private panel3d: OptionsPanel | null = null;
  private panelMatch: OptionsPanel | null = null;
  private smartsText = "";
  private matchTimer: number | undefined;
  /** True while stored molecules are loading, when the panels do not yet hold them all. */
  private restoring = false;

  constructor(readonly api: ChemistryApi) {
    this.library = new LibraryDialog(api, (smiles) => this.insertSmiles(smiles));
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
    this.store.rememberMode(mode);
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
    const lines = this.store.input.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
    if (lines.length === 0) {
      this.addPanel().startEmpty();
    }
    this.restoring = lines.length > 0;
    const loading = lines.map((line) => this.addPanel().load(line));
    void Promise.all(loading).finally(() => {
      this.restoring = false;
      this.persist();
    });
  }

  /** Adds a panel showing this SMILES. */
  private insertSmiles(smiles: string): Promise<void> {
    return this.addPanel().load(smiles);
  }

  private addPanel(): MoleculePanel {
    const panel = new MoleculePanel(this, this.mode);
    this.panels.push(panel);
    this.results.append(panel.element);
    return panel;
  }

  /** Remembers the molecules shown, so that they return when the page is reloaded. */
  private persist(): void {
    if (this.restoring) {
      return;
    }
    const smiles = this.panels.map((panel) => panel.smiles).filter((value): value is string => value !== null);
    this.store.rememberInput(smiles.join("\n"));
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

  /** Redraws the matches once the user stops changing the options. */
  private matchOptionsChanged(): void {
    window.clearTimeout(this.matchTimer);
    this.matchTimer = window.setTimeout(() => this.panels.forEach((panel) => void panel.updateMatches()), 250);
  }
}
