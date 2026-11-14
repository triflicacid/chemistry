import type { ChemistryApi, SceneResponse } from "../api/ChemistryApi";
import { ApiError } from "../api/http";
import type { GeneratedNotation, MatchData, MoleculeId, MoleculeMetadata, OptionValues, ProjectionRequest, Scene, Structure3D } from "../api/types";
import type { PanelState } from "../store";
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
  optionsMatch(): OptionsPanel | null;
  /** The SMARTS pattern to highlight, or blank. */
  smarts(): string;
  /** The panel has switched to this mode. */
  modeChosen(mode: Mode): void;
  /** The panel now shows a different molecule. */
  moleculeChanged(): void;
  /** Matching in the panel failed with this message, or (null) no longer fails. */
  matchFailed(panel: MoleculePanel, message: string | null): void;
  /** The user asked for the panel to be removed. */
  removeRequested(panel: MoleculePanel): void;
}

interface Cache {
  id: MoleculeId;
  two?: SvgView;
  three?: ThreeView;
  conformers?: unknown;
}

/** A flat drawing of the 3D structure, shown in a tab of its own. */
interface Projection {
  readonly number: number;
  readonly moleculeId: MoleculeId;
  readonly view: SvgView;
  readonly generated: GeneratedNotation | null;
  readonly tab: HTMLElement;
}

export class MoleculePanel {
  readonly element = document.createElement("div");
  private readonly results = new ResultsView((smiles) => this.open(smiles));
  private readonly body = document.createElement("div");
  private readonly chevron = document.createElement("button");
  private readonly matches = document.createElement("section");
  private matchViews: SvgView[] = [];
  /** Tells a slow match request that a newer one has replaced it. */
  private matchRequest = 0;
  private readonly projectActions: HTMLElement;
  /** The tabs and projection buttons, shown once there is a molecule to act on. */
  private readonly toolbar = document.createElement("div");
  private readonly tabs = document.createElement("div");
  private readonly modeTabs = new Map<Mode, HTMLElement>();
  private readonly projections: Projection[] = [];
  private nextProjection = 1;
  /** The tab being shown: a mode, or the number of a projection. */
  private active: Mode | number;
  private cache: Cache | null = null;
  private current: { id: MoleculeId; smiles: string; canonical: string } | null = null;
  private three: ThreeView | null = null;
  private conformers: unknown = null;

  constructor(private readonly host: PanelHost, private mode: Mode) {
    this.active = mode;
    this.element.className = "panel";
    this.projectActions = this.buildProjectActions();
    this.projectActions.hidden = mode !== "3d";
    this.toolbar.className = "toolbar";
    this.chevron.type = "button";
    this.chevron.className = "chevron";
    this.chevron.addEventListener("click", () => this.setCollapsed(!this.body.hidden));
    this.setCollapsed(false);
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "panel-remove";
    remove.textContent = "×";
    remove.title = "Remove this molecule";
    remove.setAttribute("aria-label", remove.title);
    remove.addEventListener("click", () => this.host.removeRequested(this));
    this.showTools(false);
    this.tabs.className = "tabs";
    this.tabs.setAttribute("role", "tablist");
    for (const [value, text] of [["2d", "2D"], ["3d", "3D"]] as const) {
      const tab = document.createElement("div");
      tab.className = "tab";
      tab.append(this.tabButton(text, () => void this.setMode(value)));
      this.modeTabs.set(value, tab);
      this.tabs.append(tab);
    }
    this.markActive();
    this.toolbar.append(this.chevron, this.tabs, this.projectActions, remove);
    this.matches.className = "matches";
    this.matches.hidden = true;
    this.body.className = "panel-body";
    this.body.append(this.results.element, this.matches);
    this.element.append(this.toolbar, this.body);
  }

  /** Hides or shows everything below the toolbar. */
  private setCollapsed(collapsed: boolean): void {
    this.body.hidden = collapsed;
    this.chevron.textContent = collapsed ? "▸" : "▾";
    this.chevron.title = collapsed ? "Expand" : "Collapse";
    this.chevron.setAttribute("aria-label", this.chevron.title);
    this.chevron.setAttribute("aria-expanded", String(!collapsed));
  }

  /** The tabs only mean something once there is a molecule. */
  private showTools(on: boolean): void {
    this.tabs.hidden = !on;
    this.projectActions.hidden = !on || this.mode !== "3d";
  }

  /** The SMILES of the molecule shown, or null if there is none. */
  get smiles(): string | null {
    return this.current?.smiles ?? null;
  }

  /** What to keep so that this panel comes back as it is, or null while it holds no molecule. */
  get stored(): PanelState | null {
    return this.current === null ? null : { id: this.current.id, smiles: this.current.smiles, mode: this.mode };
  }

  /** True while the panel holds no molecule and nothing has been typed into it. */
  get blank(): boolean {
    return this.current === null && this.results.entered === "";
  }

  /** Shows an empty panel for a SMILES to be typed into. */
  startEmpty(): void {
    this.results.showEntry("", null);
  }

  private tabButton(text: string, onClick: () => void): HTMLButtonElement {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "tab-label";
    button.setAttribute("role", "tab");
    button.textContent = text;
    button.addEventListener("click", onClick);
    return button;
  }

  /** Highlights the tab being shown. */
  private markActive(): void {
    const tabs = [
      ...[...this.modeTabs].map(([mode, tab]) => ({ tab, on: mode === this.active })),
      ...this.projections.map((projection) => ({ tab: projection.tab, on: projection.number === this.active })),
    ];
    for (const { tab, on } of tabs) {
      tab.classList.toggle("active", on);
      tab.querySelector(".tab-label")?.setAttribute("aria-selected", String(on));
    }
  }

  /** Creates the molecule on the server and draws it; a failure is shown in the panel rather than thrown. */
  async load(smiles: string): Promise<void> {
    this.results.showMessage(`Rendering ${smiles}...`);
    const problem = await this.open(smiles);
    if (problem !== null) {
      this.showTools(false);
      this.results.showEntry(smiles, problem);
    }
  }

  /**
   * Shows the molecule a panel was left holding, drawing it from the server's own copy where that is still there
   * and making it again from its SMILES where it is not. A failure is shown in the panel rather than thrown.
   *
   * @param state what the panel was last storing
   */
  async restore(state: PanelState): Promise<void> {
    this.results.showMessage(`Rendering ${state.smiles}...`);

    let known;
    try {
      known = await this.host.api.metadata(state.id);
    } catch {
      // the server has forgotten the molecule, a restart being the usual reason
      await this.load(state.smiles);
      return;
    }

    this.forget();
    try {
      await this.display(state.id, known.smiles, known.canonical_smiles, known, true);
    } catch (error) {
      this.showTools(false);
      this.results.showEntry(state.smiles, MoleculePanel.describe(error));
      return;
    }
    this.showTools(true);
  }

  /**
   * Replaces the molecule shown with one made from this SMILES. If it cannot be made, the panel is left as it was.
   *
   * @returns why the SMILES was refused, or null if it is now shown
   */
  async open(smiles: string): Promise<string | null> {
    let created;
    try {
      created = await this.host.api.createMolecule(smiles);
    } catch (error) {
      return MoleculePanel.describe(error);
    }

    this.forget();
    try {
      await this.display(created.id, created.smiles, created.canonical_smiles, null);
    } catch (error) {
      this.showTools(false);
      this.results.showEntry(smiles, MoleculePanel.describe(error));
      return null;
    }
    this.showTools(true);
    this.host.moleculeChanged();
    return null;
  }

  /** Lets go of the molecule shown, and everything made from it. */
  private forget(): void {
    this.matchRequest++;
    this.clearMatches();
    this.dropCache();
    this.projections.forEach((projection) => {
      projection.view.dispose();
      projection.tab.remove();
    });
    this.projections.length = 0;
    this.nextProjection = 1;
    this.active = this.mode;
    this.markActive();
    this.three = null;
    if (this.current) {
      this.host.api.deleteMolecule(this.current.id).catch(() => undefined);
    }
    this.current = null;
  }

  /** Switches to the 2D or 3D drawing, reusing a view already made. */
  async setMode(mode: Mode): Promise<void> {
    this.mode = mode;
    this.active = mode;
    this.markActive();
    this.host.modeChosen(mode);
    this.projectActions.hidden = mode !== "3d" || !this.current;
    if (!this.current) {
      return;
    }
    try {
      await this.display(this.current.id, this.current.smiles, this.current.canonical, null, true, true);
    } catch (error) {
      this.results.showError(MoleculePanel.describe(error));
    }
  }

  /** Applies changed 2D options, which the server has to draw again */
  async updateTwo(): Promise<void> {
    if (!this.current || !this.host.options2d()) {
      return;
    }
    if (this.mode !== "2d" || this.active !== "2d") {
      this.dropTwo();
      return;
    }
    const { id, smiles, canonical } = this.current;
    try {
      await this.display(id, smiles, canonical, null);
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
    this.forget();
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
    void this.updateMatches();
  }

  /**
   * Shows where the SMARTS pattern matches: in 2D, a drawing per match or one with every match; in 3D, the matches
   * highlighted in the view itself, one at a time or all together, as chosen.
   */
  async updateMatches(): Promise<void> {
    const request = ++this.matchRequest;
    this.host.matchFailed(this, null);
    const smarts = this.host.smarts();
    const options = this.host.optionsMatch();
    if (!this.current || this.active !== this.mode || !smarts || !options) {
      this.clearMatches();
      this.clearHighlights();
      return;
    }

    try {
      const { id } = this.current;
      if (this.mode === "2d") {
        const result = await this.host.api.matches2d(id, smarts, this.host.options2d()?.values() ?? {}, options.values());
        if (request !== this.matchRequest) {
          return;
        }
        this.clearMatches();
        this.clearHighlights();
        if (result.total === 0) {
          this.matches.append(MoleculePanel.matchNote(`${smarts} does not match`, false));
        } else {
          this.matches.append(MoleculePanel.matchNote(MoleculePanel.matchSummary(smarts, result), false));
          const row = document.createElement("div");
          row.className = "match-row";
          result.scenes.forEach((scene, index) => {
            const view = MoleculePanel.sceneView(scene);
            this.matchViews.push(view);
            const cell = document.createElement("div");
            cell.className = "match-cell";
            cell.append(view.element);
            if (!result.merged) {
              cell.append(MoleculePanel.smilesLine([result.smiles[index] ?? null]));
            }
            row.append(cell);
          });
          this.matches.append(row);
          if (result.merged) {
            this.matches.append(MoleculePanel.smilesLine(result.smiles));
          }
        }
      } else {
        const data = await this.host.api.matches3d(id, smarts, options.values());
        if (request !== this.matchRequest) {
          return;
        }
        this.clearMatches();
        if (!this.three || data.total === 0) {
          this.clearHighlights();
          this.matches.append(MoleculePanel.matchNote(`${smarts} does not match`, false));
        } else {
          this.matches.append(MoleculePanel.matchNote(MoleculePanel.matchSummary(smarts, data), false));
          this.showMatches3d(this.three, data);
        }
      }
      this.matches.hidden = false;
    } catch (error) {
      if (request !== this.matchRequest) {
        return;
      }
      this.clearMatches();
      this.clearHighlights();
      this.host.matchFailed(this, MoleculePanel.describe(error));
    }
  }

  /**
   * Highlights the matches in a 3D view. Unless they are merged, a button per match picks the one highlighted, and
   * pressing the one that is highlighted takes the highlights off.
   */
  private showMatches3d(view: ThreeView, data: MatchData): void {
    const smiles = document.createElement("div");
    const show = (chosen: number | null) => {
      if (chosen === null) {
        view.setMatches(null);
        smiles.replaceChildren();
        return;
      }
      const matches = data.merged ? data.matches : data.matches.slice(chosen, chosen + 1);
      view.setMatches({ matches, merged: data.merged, dim: data.dim, recolour: data.recolour, labels: data.labels });
      smiles.replaceChildren(MoleculePanel.smilesLine(matches.map((match) => match.smiles)));
    };
    show(0);
    if (data.merged) {
      this.matches.append(smiles);
      return;
    }

    let selected: number | null = 0;
    const row = document.createElement("div");
    row.className = "match-chips";
    const chips = data.matches.map((match, index) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "small";
      chip.textContent = String(index + 1);
      chip.style.borderColor = match.colour;
      chip.addEventListener("click", () => {
        selected = selected === index ? null : index;
        chips.forEach((other, i) => other.classList.toggle("active", i === selected));
        show(selected);
      });
      return chip;
    });
    chips[0]?.classList.add("active");
    row.append(...chips);
    this.matches.append(row, smiles);
  }

  private static matchSummary(smarts: string, result: { total: number; shown: number; merged: boolean }): string {
    const noun = result.total === 1 ? "match" : "matches";
    const counted = result.shown < result.total ? `${result.shown} of ${result.total} ${noun}` : `${result.total} ${noun}`;
    return `${smarts}: ${counted}${result.merged ? " (merged)" : ""}`;
  }

  /** Takes the match highlights off the 3D view, which is kept while the panel is in 2D. */
  private clearHighlights(): void {
    this.cache?.three?.setMatches(null);
  }
  private clearMatches(): void {
    this.matchViews.forEach((view) => view.dispose());
    this.matchViews = [];
    this.matches.replaceChildren();
    this.matches.hidden = true;
  }

  /** The SMILES of matched fragments as plain text, one per line. */
  private static smilesLine(smiles: (string | null)[]): HTMLElement {
    const text = document.createElement("p");
    text.className = "match-smiles";
    text.textContent = smiles.filter((each): each is string => each !== null).join("\n");
    return text;
  }

  private static matchNote(text: string, error: boolean): HTMLElement {
    const note = document.createElement("p");
    note.className = error ? "message error" : "message";
    note.textContent = text;
    return note;
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

  /** Lets go of the 2D drawing kept for the molecule shown, leaving its 3D view alone. */
  private dropTwo(): void {
    this.cache?.two?.dispose();
    if (this.cache) {
      this.cache.two = undefined;
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
    const { id } = this.current;
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
      const { scene, moleculeId } = await this.host.api.project2d(id, request);
      const number = this.nextProjection++;
      const projection: Projection = {
        number,
        moleculeId,
        view: MoleculePanel.sceneView(scene),
        generated: scene.generated ?? null,
        tab: this.buildProjectionTab(number),
      };
      this.projections.push(projection);
      this.tabs.append(projection.tab);
      await this.showProjection(projection);
    } catch (error) {
      this.results.showError(MoleculePanel.describe(error));
    }
  }

  private buildProjectionTab(number: number): HTMLElement {
    const tab = document.createElement("div");
    tab.className = "tab";
    const close = document.createElement("button");
    close.type = "button";
    close.className = "tab-close";
    close.textContent = "×";
    close.title = `Close projection #${number}`;
    close.setAttribute("aria-label", close.title);
    close.addEventListener("click", () => void this.closeProjection(number));
    tab.append(
      this.tabButton(`projection #${number}`, () => {
        const projection = this.projections.find((candidate) => candidate.number === number);
        if (projection) {
          void this.showProjection(projection);
        }
      }),
      close,
    );
    return tab;
  }

  /** Shows a projection, leaving the 3D view it came from untouched behind its tab. */
  private async showProjection(projection: Projection): Promise<void> {
    if (!this.current) {
      return;
    }
    this.active = projection.number;
    this.matchRequest++;
    this.clearMatches();
    this.markActive();
    this.projectActions.hidden = true;
    this.three = null;
    const { id } = this.current;
    const { generated } = projection;
    // the stored molecule's metadata only describes the SMILES it was entered as, so it is only used where the reading agrees
    const smiles = generated?.smiles ?? this.current.smiles;
    const canonical = generated?.canonical_smiles ?? this.current.canonical;
    const inspect = generated === null || generated.matches_source;
    try {
      await this.present(Promise.resolve({ view: projection.view, moleculeId: projection.moleculeId }), id, smiles, canonical, null, false, inspect);
      if (generated && generated.issues.length > 0) {
        this.results.note(`Stereochemistry left unspecified: ${generated.issues.join("; ")}`);
      }
    } catch (error) {
      this.results.showError(MoleculePanel.describe(error));
    }
  }

  private async closeProjection(number: number): Promise<void> {
    const index = this.projections.findIndex((projection) => projection.number === number);
    if (index < 0) {
      return;
    }
    const [projection] = this.projections.splice(index, 1) as [Projection];
    projection.tab.remove();
    if (this.active === number) {
      // back to the drawing it was made from, so that the view is not still showing when it is disposed
      await this.setMode(this.mode);
    }
    projection.view.dispose();
  }

  private static sceneView(scene: Scene): SvgView {
    const view = new SvgView();
    view.setScene(scene);
    return view;
  }

  /** @param track whether this is the molecule's own drawing, which later switches of mode refetch from */
  private async present(
    rendering: Promise<{ view: SvgView | ThreeView; moleculeId: MoleculeId }>,
    id: MoleculeId,
    smiles: string,
    canonical: string,
    known: MoleculeMetadata | null,
    track = true,
    inspect = true,
  ): Promise<void> {
    // fetching both at once keeps the card from growing a second time once the metadata lands
    const [{ view, moleculeId }, metadata] = await Promise.all([rendering, known ?? (inspect ? this.inspectionOf(id) : null)]);
    if (track) {
      this.current = { id: moleculeId, smiles, canonical };
    }
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
