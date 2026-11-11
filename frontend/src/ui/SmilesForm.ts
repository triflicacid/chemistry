export class SmilesForm {
  readonly element = document.createElement("form");
  private readonly input = document.createElement("textarea");
  private readonly button = document.createElement("button");

  constructor(onSubmit: (smiles: string) => void) {
    const label = document.createElement("label");
    label.textContent = "SMILES";
    label.htmlFor = this.input.id = "smiles";

    this.input.rows = 4;
    this.input.placeholder = "e.g. CC(=O)Oc1ccccc1C(=O)O";
    this.input.spellcheck = false;
    this.input.autocomplete = "off";

    this.button.type = "submit";
    this.button.textContent = "RENDER";

    this.element.append(label, this.input, this.button);
    this.element.addEventListener("submit", (event) => {
      event.preventDefault();
      onSubmit(this.input.value.trim());
    });
    // Ctrl/Cmd+Enter submits from the textarea
    this.input.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
        event.preventDefault();
        this.element.requestSubmit();
      }
    });
  }

  /** Disables the button while a request is running. */
  setBusy(busy: boolean): void {
    this.button.disabled = busy;
    this.button.textContent = busy ? "RENDERING..." : "RENDER";
  }
}
