/** localStorage key. */
const LAYOUT_KEY = "studio.layout";

/** How wide or tall each panel is, in pixels, and which of them are collapsed. */
export interface LayoutState {
  outliner: number;
  inspector: number;
  analysis: number;
  outlinerCollapsed: boolean;
  inspectorCollapsed: boolean;
  analysisCollapsed: boolean;
}

export const DEFAULT_LAYOUT: LayoutState = {
  outliner: 240,
  inspector: 300,
  analysis: 160,
  outlinerCollapsed: false,
  inspectorCollapsed: false,
  analysisCollapsed: true,
};

/** The range each panel may be dragged to, so that the viewport keeps most of the space. */
const LIMITS = {
  outliner: { least: 180, most: 480 },
  inspector: { least: 220, most: 520 },
  analysis: { least: 96, most: 480 },
} as const;

/** Which panels can be sized and collapsed. */
export type Panel = keyof typeof LIMITS;

/** Holds a size inside the range its panel allows. */
export function clamp(panel: Panel, size: number): number {
  const { least, most } = LIMITS[panel];
  return Math.min(most, Math.max(least, Math.round(size)));
}

/** Remembers how the user arranged the workspace. */
export class StudioStore {
  private layoutState = StudioStore.readLayout();

  get layout(): LayoutState {
    return this.layoutState;
  }

  rememberLayout(layout: LayoutState): void {
    this.layoutState = layout;
    try {
      localStorage.setItem(LAYOUT_KEY, JSON.stringify(layout));
    } catch {
      // a browser that refuses storage still gets a working workspace, just not a remembered one
    }
  }

  /** Reads back what was stored, filling in anything missing or out of range from the defaults. */
  private static readLayout(): LayoutState {
    let stored: unknown;
    try {
      stored = JSON.parse(localStorage.getItem(LAYOUT_KEY) ?? "null");
    } catch {
      return DEFAULT_LAYOUT;
    }

    const held = stored as Partial<LayoutState> | null;
    if (!held || typeof held !== "object") {
      return DEFAULT_LAYOUT;
    }

    return {
      outliner: size(held.outliner, "outliner"),
      inspector: size(held.inspector, "inspector"),
      analysis: size(held.analysis, "analysis"),
      outlinerCollapsed: held.outlinerCollapsed ?? DEFAULT_LAYOUT.outlinerCollapsed,
      inspectorCollapsed: held.inspectorCollapsed ?? DEFAULT_LAYOUT.inspectorCollapsed,
      analysisCollapsed: held.analysisCollapsed ?? DEFAULT_LAYOUT.analysisCollapsed,
    };
  }
}

function size(stored: unknown, panel: Panel): number {
  return typeof stored === "number" && Number.isFinite(stored) ? clamp(panel, stored) : DEFAULT_LAYOUT[panel];
}
