import {asFinish, type Finish} from "../../render/finish";
import {Observable} from "./observable";

/** localStorage key. */
const RENDER_KEY = "studio.render";

/** How dim and how bright the scene can be made, as a multiple of its usual lighting. */
export const MIN_BRIGHTNESS = 0.3;
export const MAX_BRIGHTNESS = 2;

/** The usual lighting. */
export const DEFAULT_BRIGHTNESS = 1;

/** How the molecule is drawn, which the viewport shows and the Render options section changes. */
export interface RenderState {
  finish: Finish;
  /** A multiple of the scene's usual lighting. */
  brightness: number;
  /** Whether the Render options section is open. */
  open: boolean;
}

/**
 * @param brightness a multiple of the usual lighting, which may be out of range or not a number
 * @returns the nearest brightness the scene can have
 */
export function clampBrightness(brightness: number): number {
  return Number.isFinite(brightness) ? Math.min(MAX_BRIGHTNESS, Math.max(MIN_BRIGHTNESS, brightness)) : DEFAULT_BRIGHTNESS;
}

/** How the molecule is drawn, remembered between visits. */
export class RenderModel extends Observable<RenderState> {
  constructor() {
    super(RenderModel.read());
  }

  /**
   * Chooses how shiny atoms and bonds are.
   *
   * @param finish matte or glossy
   */
  finishWith(finish: Finish): void {
    if (this.get().finish !== finish) {
      this.change({ ...this.get(), finish });
    }
  }

  /**
   * Chooses how bright the scene is.
   *
   * @param brightness a multiple of the usual lighting, which is held to the range the scene can have
   */
  brightnessTo(brightness: number): void {
    const next = clampBrightness(brightness);
    if (this.get().brightness !== next) {
      this.change({ ...this.get(), brightness: next });
    }
  }

  /**
   * Opens or closes the Render options section.
   *
   * @param open whether it is open
   */
  openTo(open: boolean): void {
    if (this.get().open !== open) {
      this.change({ ...this.get(), open });
    }
  }

  private change(state: RenderState): void {
    this.set(state);
    try {
      localStorage.setItem(RENDER_KEY, JSON.stringify(state));
    } catch {
      // a browser that refuses storage still gets the settings, just not remembered ones
    }
  }

  /** Reads back what was stored, filling in anything missing or unusable from the defaults. */
  private static read(): RenderState {
    const defaults: RenderState = { finish: "matte", brightness: DEFAULT_BRIGHTNESS, open: false };
    let stored: unknown;
    try {
      stored = JSON.parse(localStorage.getItem(RENDER_KEY) ?? "null");
    } catch {
      return defaults;
    }

    const held = stored as Partial<RenderState> | null;
    if (!held || typeof held !== "object") {
      return defaults;
    }
    return {
      finish: asFinish(held.finish),
      brightness: typeof held.brightness === "number" ? clampBrightness(held.brightness) : defaults.brightness,
      open: typeof held.open === "boolean" ? held.open : defaults.open,
    };
  }
}
