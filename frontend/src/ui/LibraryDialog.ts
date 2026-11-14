import type { ChemistryApi } from "../api/ChemistryApi";
import type { LibraryEntry } from "../api/types";

const SEARCH_DELAY_MS = 200;

/** A popup for finding a molecule in the server's library and inserting it as a new panel. */
export class LibraryDialog {
  readonly element = document.createElement("dialog");
  private readonly name = document.createElement("input");
  private readonly select = document.createElement("select");
  private readonly chosen = document.createElement("div");
  private readonly count = document.createElement("span");
  private readonly message = document.createElement("p");
  private readonly body = document.createElement("tbody");
  private readonly categories = new Set<string>();
  private loaded = false;
  private request = 0;
  private timer: number | undefined;

  constructor(private readonly api: ChemistryApi, private readonly onInsert: (smiles: string) => Promise<void> | void) {
    this.element.className = "library-dialog";

    const title = document.createElement("h2");
    title.textContent = "Molecule library";

    const close = document.createElement("button");
    close.type = "button";
    close.className = "library-close";
    close.textContent = "\u00d7";
    close.title = "Close";
    close.addEventListener("click", () => this.element.close());

    const header = document.createElement("div");
    header.className = "library-header";
    header.append(title, close);

    this.name.type = "search";
    this.name.placeholder = "Search by name";
    this.name.autocomplete = "off";
    this.name.spellcheck = false;
    this.name.addEventListener("input", () => this.searchSoon());

    this.select.addEventListener("change", () => {
      if (this.select.value) {
        this.categories.add(this.select.value);
        this.renderCategories();
        void this.search();
      }
    });

    this.chosen.className = "library-chosen";
    this.count.className = "library-count";

    const filters = document.createElement("div");
    filters.className = "library-filters";
    filters.append(this.name, this.select, this.count);

    this.message.className = "message error";
    this.message.hidden = true;

    const head = document.createElement("thead");
    head.innerHTML = "<tr><th>Name</th><th>SMILES</th><th>Categories</th><th></th></tr>";
    const table = document.createElement("table");
    table.className = "library-table";
    table.append(head, this.body);

    const scroller = document.createElement("div");
    scroller.className = "library-results";
    scroller.append(table);

    this.element.append(header, filters, this.chosen, this.message, scroller);
    this.element.addEventListener("click", (event) => {
      if (event.target === this.element) {
        this.element.close();
      }
    });
  }

  open(): void {
    if (!this.element.open) {
      this.element.showModal();
    }
    this.name.focus();
    if (!this.loaded) {
      this.loaded = true;
      void this.loadCategories();
      void this.search();
    }
  }

  private async loadCategories(): Promise<void> {
    try {
      const names = await this.api.libraryCategories();
      this.select.replaceChildren(new Option("Add a category...", ""), ...names.map((category) => new Option(category, category)));
      this.renderCategories();
    } catch (error) {
      this.loaded = false;
      this.showProblem(error);
    }
  }

  /** Redraws the pills. */
  private renderCategories(): void {
    this.chosen.replaceChildren(
      ...[...this.categories].sort().map((category) => {
        const pill = document.createElement("button");
        pill.type = "button";
        pill.className = "library-pill";
        pill.textContent = category;
        pill.title = `Remove ${category}`;
        pill.addEventListener("click", () => {
          this.categories.delete(category);
          this.renderCategories();
          void this.search();
        });
        return pill;
      }),
    );
    for (const option of this.select.options) {
      option.disabled = this.categories.has(option.value);
    }
    this.select.value = "";
  }

  private searchSoon(): void {
    window.clearTimeout(this.timer);
    this.timer = window.setTimeout(() => void this.search(), SEARCH_DELAY_MS);
  }

  private async search(): Promise<void> {
    window.clearTimeout(this.timer);
    const request = ++this.request;
    try {
      const found = await this.api.searchLibrary(this.name.value.trim(), [...this.categories]);
      if (request === this.request) {
        this.message.hidden = true;
        this.showResults(found);
      }
    } catch (error) {
      if (request === this.request) {
        this.showProblem(error);
      }
    }
  }

  private showProblem(error: unknown): void {
    this.message.textContent = `Library unavailable: ${error instanceof Error ? error.message : String(error)}`;
    this.message.hidden = false;
  }

  private showResults(found: LibraryEntry[]): void {
    const sorted = [...found].sort((a, b) => a.name.localeCompare(b.name));
    this.count.textContent = `${sorted.length} ${sorted.length === 1 ? "molecule" : "molecules"}`;
    this.body.replaceChildren(...sorted.map((entry) => this.row(entry)));
    if (sorted.length === 0) {
      const cell = document.createElement("td");
      cell.colSpan = 4;
      cell.className = "library-empty";
      cell.textContent = "No molecules match.";
      const row = document.createElement("tr");
      row.append(cell);
      this.body.append(row);
    }
  }

  private row(entry: LibraryEntry): HTMLTableRowElement {
    const name = document.createElement("td");
    name.textContent = entry.name;

    const smiles = document.createElement("td");
    smiles.className = "library-smiles";
    smiles.textContent = entry.smiles;

    const classes = document.createElement("td");
    classes.className = "library-classes";
    classes.textContent = entry.classes.join(", ");

    const insert = document.createElement("button");
    insert.type = "button";
    insert.className = "small";
    insert.textContent = "Insert";
    insert.addEventListener("click", async () => {
      insert.disabled = true;
      try {
        await this.onInsert(entry.smiles);
        insert.textContent = "Inserted \u2713";
      } finally {
        window.setTimeout(() => {
          insert.textContent = "Insert";
          insert.disabled = false;
        }, 1200);
      }
    });
    const action = document.createElement("td");
    action.append(insert);

    const row = document.createElement("tr");
    row.append(name, smiles, classes, action);
    return row;
  }
}
