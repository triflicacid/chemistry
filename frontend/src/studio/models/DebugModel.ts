import {Observable} from "./observable";

/** Whether debug mode is on, in which what is shown comes with the numbers behind it. */
export class DebugModel extends Observable<boolean> {
  constructor() {
    super(false);
  }

  /**
   * @param on whether debug mode is on
   */
  to(on: boolean): void {
    if (this.get() !== on) {
      this.set(on);
    }
  }
}
