import {internalTarget} from "./measurementTarget";
import {SelectMode} from "./SelectMode";
import type {ModeHost, ModeOptions} from "./ToolMode";

/** Turning things: atoms and bonds. */
export class RotateMode extends SelectMode {
  /** What the handles on the molecule last did. */
  private handleMode: "rotate" | null = null;

  /**
   * @param host the view the mode is for
   */
  constructor(host: ModeHost) {
    super(host, "rotate");
  }

  override exit(): void {
    this.abandonBox();
    this.handleMode = null;
    this.host.molecule.setMode(null);
    this.host.bonds.set(null);
    this.host.setPlain(false);
    this.host.setMeasured(true);
  }

  override configure(options: ModeOptions): void {
    const { molecule, bonds, scene } = this.host;
    const turning = options.rotateTarget === "molecule";

    this.host.setMeasured(false);
    // turning the whole molecule shows it by its handles, so only what is hovered is tinted, and nothing is labelled
    this.host.setPlain(turning);

    const handleMode = turning && options.selection.some((item) => item.kind === "molecule") ? "rotate" : null;
    if (handleMode !== this.handleMode) {
      this.handleMode = handleMode;
      molecule.setMode(handleMode);
    }

    bonds.set(turning ? null : internalTarget(options, scene.locked));
  }
}
