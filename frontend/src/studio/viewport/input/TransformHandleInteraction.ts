import type {Stage} from "../../../render/stage";
import type {InteractionHandler} from "./PointerRouter";

/**
 * The molecule's handles and the bonds' handles. Each is a transform control that hears the press itself, so this
 * only keeps the camera still while one is under the pointer, and leaves the press to be heard.
 */
export class TransformHandleInteraction implements InteractionHandler {
  readonly swallows = false;

  /**
   * @param stage the stage whose camera must stay put
   * @param hovered whether the pointer is over one of the handles
   */
  constructor(
    private readonly stage: Stage,
    private readonly hovered: () => boolean,
  ) {}

  hitTest(): boolean {
    return this.hovered();
  }

  press(event: PointerEvent): boolean {
    if (event.button !== 0 || !this.hovered()) {
      return false;
    }

    // a handle is under the pointer, which takes hold of it, and the camera must stay put
    this.stage.controls.enabled = false;
    return true;
  }
}
