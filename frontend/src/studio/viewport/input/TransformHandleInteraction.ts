import type {CameraLock} from "../camera/CameraLock";
import type {InteractionHandler} from "./PointerRouter";

/**
 * The molecule's handles and the bonds' handles. Each is a transform control that hears the press itself, so this
 * only keeps the camera still while one is under the pointer, and leaves the press to be heard.
 */
export class TransformHandleInteraction implements InteractionHandler {
  readonly swallows = false;

  /**
   * @param lock keeps the camera's own controls off, which must be done before they hear the press
   * @param hovered whether the pointer is over one of the handles
   */
  constructor(
    private readonly lock: CameraLock,
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
    this.lock.hold("transformHandle");
    return true;
  }

  // the handles hold the camera themselves once they have hold of the press, so this hold is only to be first to do so
  release(): void {
    this.lock.release("transformHandle");
  }

  cancel(): void {
    this.lock.release("transformHandle");
  }
}
