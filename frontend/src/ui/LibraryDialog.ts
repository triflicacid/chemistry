const SEARCH_DELAY_MS = 200;

/** One tab of the library popup: what it holds, and how to find and show it. */
export interface LibraryTab {
  /** The text on the tab. */
  readonly label: string;
  /** What to show in the empty search box. */
  readonly placeholder: string;
  /** The singular and the plural of what the tab holds, for the count beside the filters. */
  readonly nouns: readonly [string, string];
  /** The column headings, not counting the one the buttons sit under. */
  readonly headings: readonly string[];
  /** Every category the tab's entries use. */
  categories(): Promise<string[]>;
  /**
   * Finds entries and builds a row for each.
   *
   * @param name the part of a name to look for, or blank for any
   * @param categories the categories an entry must all belong to; may be empty
   */
  rows(name: string, categories: string[]): Promise<HTMLTableRowElement[]>;
}

/** What the user has typed and chosen in one tab, kept while they look at another. */
interface Filters {
  name: string;
  categories: Set<string>;
}

/**
 * A row of a library tab.
 *
 * @param cells one per heading; a string becomes a plain cell
 * @param actions the buttons to put in the last cell
 */
export function libraryRow(cells: (string | HTMLElement)[], actions: HTMLElement[]): HTMLTableRowElement {
  const row = document.createElement("tr");
  for (const content of cells) {
    const cell = document.createElement("td");
    if (typeof content === "string") {
      cell.textContent = content;
    } else {
      cell.append(content);
    }
    row.append(cell);
  }
  const last = document.createElement("td");
  last.className = "library-actions";
  last.append(...actions);
  row.append(last);
  return row;
}

/** A cell whose text is shown in the given style, such as the monospace used for notation. */
export function libraryCell(text: string, className: string): HTMLElement {
  const span = document.createElement("span");
  span.className = className;
  span.textContent = text;
  return span;
}

/**
 * A button on a row, which reports what it did by ticking until it is ready again.
 *
 * @param label the text on it
 * @param hint what it does, shown on hovering
 * @param run what it does
 */
export function libraryAction(label: string, hint: string, run: () => Promise<void> | void): HTMLButtonElement {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "small";
  button.textContent = label;
  button.title = hint;
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await run();
      button.textContent = `${label} ✓`;
    } finally {
      window.setTimeout(() => {
        button.textContent = label;
        button.disabled = false;
      }, 1200);
    }
  });
  return button;
}

/** A popup for finding something in the server's libraries, a tab to each library. */
export class LibraryDialog {
  readonly element = document.createElement("dialog");
  private readonly name = document.createElement("input");
  private readonly select = document.createElement("select");
  private readonly chosen = document.createElement("div");
  private readonly count = document.createElement("span");
  private readonly message = document.createElement("p");
  private readonly head = document.createElement("thead");
  private readonly body = document.createElement("tbody");
  private readonly tabs = document.createElement("div");
  private readonly buttons = new Map<LibraryTab, HTMLElement>();
  private readonly filters = new Map<LibraryTab, Filters>();
  /** The tabs whose categories have been fetched. */
  private readonly loaded = new Set<LibraryTab>();
  private active: LibraryTab;
  private request = 0;
  private timer: number | undefined;

  /** @param tabs the libraries to show, the first of them to begin on */
  constructor(tabList: LibraryTab[]) {
    const [first] = tabList;
    if (!first) {
      throw new Error("A library dialog needs at least one tab");
    }
    this.active = first;
    tabList.forEach((tab) => this.filters.set(tab, { name: "", categories: new Set() }));

    this.element.className = "library-dialog";

    const title = document.createElement("h2");
    title.textContent = "Library";

    const close = document.createElement("button");
    close.type = "button";
    close.className = "library-close";
    close.textContent = "×";
    close.title = "Close";
    close.addEventListener("click", () => this.element.close());

    const header = document.createElement("div");
    header.className = "library-header";
    header.append(title, close);

    this.tabs.className = "tabs";
    this.tabs.setAttribute("role", "tablist");
    for (const tab of tabList) {
      const holder = document.createElement("div");
      holder.className = "tab";
      const button = document.createElement("button");
      button.type = "button";
      button.className = "tab-label";
      button.setAttribute("role", "tab");
      button.textContent = tab.label;
      button.addEventListener("click", () => void this.show(tab));
      holder.append(button);
      this.buttons.set(tab, holder);
      this.tabs.append(holder);
    }

    this.name.type = "search";
    this.name.autocomplete = "off";
    this.name.spellcheck = false;
    this.name.addEventListener("input", () => this.searchSoon());

    this.select.addEventListener("change", () => {
      if (this.select.value) {
        this.current.categories.add(this.select.value);
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

    const table = document.createElement("table");
    table.className = "library-table";
    table.append(this.head, this.body);

    const scroller = document.createElement("div");
    scroller.className = "library-results";
    scroller.append(table);

    this.element.append(header, this.tabs, filters, this.chosen, this.message, scroller);
    this.element.addEventListener("click", (event) => {
      if (event.target === this.element) {
        this.element.close();
      }
    });
    this.markActive();
    this.renderHeadings();
  }

  open(): void {
    if (!this.element.open) {
      this.element.showModal();
    }
    this.name.focus();
    void this.show(this.active);
  }

  /** The filters of the tab being shown. */
  private get current(): Filters {
    return this.filters.get(this.active) as Filters;
  }

  /** Switches to a tab, putting back the filters it was last left with. */
  private async show(tab: LibraryTab): Promise<void> {
    if (tab !== this.active) {
      this.current.name = this.name.value;
      this.active = tab;
      this.markActive();
      this.renderHeadings();
    }
    this.name.value = this.current.name;
    this.name.placeholder = tab.placeholder;
    if (!this.loaded.has(tab)) {
      await this.loadCategories(tab);
    }
    this.renderCategories();
    await this.search();
  }

  /** Highlights the tab being shown. */
  private markActive(): void {
    for (const [tab, holder] of this.buttons) {
      const on = tab === this.active;
      holder.classList.toggle("active", on);
      holder.querySelector(".tab-label")?.setAttribute("aria-selected", String(on));
    }
  }

  private renderHeadings(): void {
    const row = document.createElement("tr");
    for (const heading of [...this.active.headings, ""]) {
      const cell = document.createElement("th");
      cell.textContent = heading;
      row.append(cell);
    }
    this.head.replaceChildren(row);
  }

  private async loadCategories(tab: LibraryTab): Promise<void> {
    try {
      const names = await tab.categories();
      this.loaded.add(tab);
      this.select.replaceChildren(new Option("Add a category...", ""), ...names.map((category) => new Option(category, category)));
    } catch (error) {
      this.showProblem(error);
    }
  }

  /** Redraws the pills. */
  private renderCategories(): void {
    const { categories } = this.current;
    this.chosen.replaceChildren(
      ...[...categories].sort().map((category) => {
        const pill = document.createElement("button");
        pill.type = "button";
        pill.className = "library-pill";
        pill.textContent = category;
        pill.title = `Remove ${category}`;
        pill.addEventListener("click", () => {
          categories.delete(category);
          this.renderCategories();
          void this.search();
        });
        return pill;
      }),
    );
    for (const option of this.select.options) {
      option.disabled = categories.has(option.value);
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
    const tab = this.active;
    this.current.name = this.name.value;
    try {
      const rows = await tab.rows(this.name.value.trim(), [...this.current.categories]);
      if (request === this.request) {
        this.message.hidden = true;
        this.showRows(tab, rows);
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

  private showRows(tab: LibraryTab, rows: HTMLTableRowElement[]): void {
    const [one, many] = tab.nouns;
    this.count.textContent = `${rows.length} ${rows.length === 1 ? one : many}`;
    this.body.replaceChildren(...rows);
    if (rows.length === 0) {
      const cell = document.createElement("td");
      cell.colSpan = tab.headings.length + 1;
      cell.className = "library-empty";
      cell.textContent = `No ${many} match.`;
      const row = document.createElement("tr");
      row.append(cell);
      this.body.append(row);
    }
  }
}
