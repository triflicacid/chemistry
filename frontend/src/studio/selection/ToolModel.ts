import type { Constraint } from "../viewport/dragPlane";
import { Observable } from "./observable";

/** What a left-drag on an atom does. */
export type Tool = "select" | "move";

/** The tool in use, and what a move is held to. */
export interface ToolState {
  tool: Tool;
  constraint: Constraint;
}

/** Which tool is in use. The toolbar and the viewport share it, and neither writes to the other. */
export class ToolModel extends Observable<ToolState> {
  constructor() {
    super({ tool: "select", constraint: "free" });
  }

  /**
   * Switches tool. The constraint is kept, so coming back to move finds it as it was left.
   *
   * @param tool the tool to use
   */
  use(tool: Tool): void {
    if (this.get().tool !== tool) {
      this.set({ ...this.get(), tool });
    }
  }

  /**
   * Holds moves to an axis or plane, or lets them go.
   *
   * @param constraint what a move is held to
   */
  constrain(constraint: Constraint): void {
    if (this.get().constraint !== constraint) {
      this.set({ ...this.get(), constraint });
    }
  }
}
