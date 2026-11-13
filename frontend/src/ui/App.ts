import type { ChemistryApi } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import { ViewStore } from "../store";
import { MoleculePanel, type Mode, type PanelHost } from "./MoleculePanel";
import { OptionsPanel } from "./OptionsPanel";
import { SmilesForm } from "./SmilesForm";

export class App implements PanelHost {
  private readonly store = new ViewStore();
  private readonly form = new SmilesForm((input) => void this.render(input));
  private readonly results = document.createElement("section");
  private readonly optionsSlot = document.createElement("div");
  private panels: MoleculePanel[] = [];
  private mode: Mode = this.store.mode;
  private panel2d: OptionsPanel | null = null;
  private panel3d: OptionsPanel | null = null;

  constructor(readonly api: ChemistryApi) {
    this.results.className = "results";
  }

  options2d(): OptionsPanel | null {
    return this.panel2d;
  }

  options3d(): OptionsPanel | null {
    return this.panel3d;
  }

  modeChosen(mode: Mode): void {
    this.mode = mode;
    this.store.rememberMode(mode);
  }

  mount(root: HTMLElement): void {
    root.append(this.form.element, this.optionsSlot, this.results);
    void this.loadOptions();

    const input = this.store.input;
    if (input.trim()) {
      this.form.setSmiles(input);
      void this.render(input);
    } else {
      this.showMessage("Enter a SMILES string, or several on separate lines, and press RENDER.");
    }
  }

  private async loadOptions(): Promise<void> {
    try {
      const [descriptors2d, descriptors3d] = await Promise.all([this.api.options2d(), this.api.options3d()]);
      this.panel2d = new OptionsPanel(descriptors2d);
      this.panel3d = new OptionsPanel(descriptors3d);
      this.panel3d.onChange = () => this.panels.forEach((panel) => void panel.updateThree());
      this.panel2d.toggle.textContent = "2D options";
      this.panel3d.toggle.textContent = "3D options";
      this.form.addAction(this.panel2d.toggle);
      this.form.addAction(this.panel3d.toggle);
      this.optionsSlot.replaceChildren(this.panel2d.element, this.panel3d.element);
    } catch (error) {
      this.optionsSlot.textContent = `Options unavailable: ${error instanceof ApiError ? error.message : String(error)}`;
    }
  }

  private showMessage(text: string): void {
    const message = document.createElement("p");
    message.className = "message";
    message.textContent = text;
    this.results.replaceChildren(message);
  }

  /** Draws a panel for each non-blank line. */
  private async render(input: string): Promise<void> {
    this.store.rememberInput(input);
    const lines = input.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
    if (lines.length === 0) {
      this.showMessage("Please enter a SMILES string.");
      return;
    }

    this.form.setBusy(true);
    try {
      this.panels.forEach((panel) => panel.dispose());
      this.panels = lines.map(() => new MoleculePanel(this, this.mode));
      this.results.replaceChildren(...this.panels.map((panel) => panel.element));
      await Promise.all(this.panels.map((panel, index) => panel.load(lines[index]!)));
    } finally {
      this.form.setBusy(false);
    }
  }
}
