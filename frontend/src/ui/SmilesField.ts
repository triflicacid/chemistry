import type { SmilesText } from "./SmilesText";

/** A SMILES string shown as text, which turns into an input when clicked. */
export class SmilesField {
  readonly element = document.createElement("div");
  private readonly form = document.createElement("form");
  private readonly input = document.createElement("input");
  private readonly apply = document.createElement("button");
  private readonly error = document.createElement("pre");
  private busy = false;

  /**
   * @param display the text shown while not editing
   * @param commit applies a new SMILES; resolves to why it was refused, or null if it was taken
   * @param editing whether to start with the input open
   */
  constructor(
    private readonly display: SmilesText,
    private readonly commit: (smiles: string) => Promise<string | null>,
    editing = false,
  ) {
    this.element.className = "smiles-field";
    this.display.element.classList.add("editable");
    this.display.element.title = "Click to edit";
    this.display.element.addEventListener("click", () => this.open());

    this.form.className = "smiles-edit";
    this.input.type = "text";
    this.input.className = "smiles-input";
    this.input.placeholder = "SMILES, e.g. CC(=O)Oc1ccccc1C(=O)O";
    this.input.spellcheck = false;
    this.input.autocomplete = "off";
    this.apply.type = "submit";
    this.apply.className = "small";
    this.apply.textContent = "Render";
    this.apply.title = "Draw this SMILES (Enter)";
    this.error.className = "message error";
    this.error.hidden = true;
    this.form.hidden = true;
    this.form.append(this.input, this.apply);
    this.element.append(this.display.element, this.form, this.error);

    this.form.addEventListener("submit", (event) => {
      event.preventDefault();
      void this.submit();
    });
    this.input.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        this.close();
      }
    });
    this.form.addEventListener("focusout", (event) => {
      if (!this.form.contains(event.relatedTarget as Node | null)) {
        this.close();
      }
    });

    if (editing) {
      this.open();
    }
  }

  /** Shows why the last SMILES was not taken. */
  setError(message: string | null): void {
    this.error.textContent = message ?? "";
    this.error.hidden = !message;
  }

  private open(): void {
    this.input.value = this.display.value;
    this.display.element.hidden = true;
    this.form.hidden = false;
    // the field may not be in the page yet
    queueMicrotask(() => {
      this.input.focus();
      this.input.select();
    });
  }

  /** Goes back to showing the text; a blank one stays open, as there is nothing to show. */
  private close(): void {
    if (this.busy || this.display.value === "") {
      return;
    }
    this.form.hidden = true;
    this.display.element.hidden = false;
    this.setError(null);
  }

  private async submit(): Promise<void> {
    const value = this.input.value.trim();
    if (value === "") {
      this.setError("Enter a SMILES string");
      return;
    }
    if (value === this.display.value) {
      this.close();
      return;
    }

    this.busy = true;
    this.input.disabled = this.apply.disabled = true;
    this.setError(null);
    try {
      this.setError(await this.commit(value));
    } finally {
      this.busy = false;
      this.input.disabled = this.apply.disabled = false;
    }
    if (!this.error.hidden) {
      this.input.focus();
    }
  }
}
