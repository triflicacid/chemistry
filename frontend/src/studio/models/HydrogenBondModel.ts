import {Observable} from "./observable";

/** A hydrogen bond to point out in the scene. */
export interface HydrogenBondMark {
  /** The hydrogen. */
  hydrogen: string;
  /** The atom it is bonded to. */
  acceptor: string;
  /** What to write beside the bond. */
  text: string;
}

/** The hydrogen bond that the scan is on, which the viewport draws while debug mode is on. */
export class HydrogenBondModel extends Observable<HydrogenBondMark | null> {
  constructor() {
    super(null);
  }

  /**
   * @param mark the bond to point out, or null to point out none
   */
  to(mark: HydrogenBondMark | null): void {
    const held = this.get();
    if (held?.hydrogen !== mark?.hydrogen || held?.acceptor !== mark?.acceptor || held?.text !== mark?.text) {
      this.set(mark);
    }
  }
}
