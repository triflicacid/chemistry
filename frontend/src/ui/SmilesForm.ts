export class SmilesForm {
  readonly element = document.createElement("form");
  private readonly input = document.createElement("textarea");
  private readonly smarts = document.createElement("input");
  private readonly button = document.createElement("button");
  private readonly actions = document.createElement("div");

  /** Adds a control next to the RENDER button. */
  addAction(control: HTMLElement): void {
    this.actions.append(control);
  }

  constructor(onSubmit: (smiles: string, smarts: string) => void) {
    const label = document.createElement("label");
    label.textContent = "SMILES";
    label.htmlFor = this.input.id = "smiles";

    this.input.rows = 4;
    this.input.placeholder = "e.g. CC(=O)Oc1ccccc1C(=O)O (one SMILES per line)";
    this.input.spellcheck = false;
    this.input.autocomplete = "off";

    const smartsLabel = document.createElement("label");
    smartsLabel.textContent = "SMARTS (optional)";
    smartsLabel.htmlFor = this.smarts.id = "smarts";

    this.smarts.type = "text";
    this.smarts.placeholder = "SMARTS, e.g. C(=O)O";
    this.smarts.spellcheck = false;
    this.smarts.autocomplete = "off";

    this.button.type = "submit";
    this.button.textContent = "RENDER";

    this.actions.className = "actions";
    this.actions.append(this.button);

    this.element.append(label, this.input, smartsLabel, this.smarts, this.actions);
    this.element.addEventListener("submit", (event) => {
      event.preventDefault();
      onSubmit(this.input.value, this.smarts.value.trim());
    });
    // Ctrl/Cmd+Enter submits from the textarea
    this.input.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
        event.preventDefault();
        this.element.requestSubmit();
      }
    });
  }

  /** Replaces what is in the SMARTS input. */
  setSmarts(smarts: string): void {
    this.smarts.value = smarts;
  }

  /** Replaces what is in the input. */
  setSmiles(smiles: string): void {
    this.input.value = smiles;
  }

  /** Disables the button while a request is running. */
  setBusy(busy: boolean): void {
    this.button.disabled = busy;
    this.button.textContent = busy ? "RENDERING..." : "RENDER";
  }
}
