/** The SMARTS pattern. */
export class SearchForm {
  readonly element = document.createElement("form");
  private readonly input = document.createElement("input");
  private readonly actions = document.createElement("div");
  private readonly problem = document.createElement("pre");

  setProblem(message: string | null): void {
    this.problem.textContent = message ?? "";
    this.problem.hidden = !message;
  }

  /** Adds a control next to the SEARCH button. */
  addAction(control: HTMLElement): void {
    this.actions.append(control);
  }

  /** @param onSubmit called with the pattern when the user presses Enter or SEARCH; blank clears the matches */
  constructor(onSubmit: (smarts: string) => void) {
    const label = document.createElement("label");
    label.textContent = "Search:";
    label.htmlFor = this.input.id = "smarts";

    this.input.type = "text";
    this.input.placeholder = "SMARTS, e.g. C(=O)O";
    this.input.spellcheck = false;
    this.input.autocomplete = "off";

    const button = document.createElement("button");
    button.type = "submit";
    button.textContent = "SEARCH";

    const row = document.createElement("div");
    row.className = "search-row";
    row.append(label, this.input, button);

    this.problem.className = "message error";
    this.problem.hidden = true;
    this.actions.className = "actions";
    this.element.append(row, this.problem, this.actions);
    this.element.addEventListener("submit", (event) => {
      event.preventDefault();
      onSubmit(this.input.value.trim());
    });
  }

  /** Replaces what is in the input. */
  setSmarts(smarts: string): void {
    this.input.value = smarts;
  }
}
