import type {Vec3} from "../../api/types/structure";
import type {BondEditAck, EditApplied, OptimiseResult, PatchAck, Pose, SessionState} from "./messages";

/**
 * The state after the session accepted a change, without waiting for it to send the whole state again.
 *
 * @param base the state the change was made on
 * @param moves the atoms that moved and where each went
 * @param ack the session's acknowledgement, which says the version and history it is now at
 * @param transform where the molecule now sits, if the change moved it as a whole
 */
export function applyAck(base: SessionState, moves: { id: string; position: Vec3 }[], ack: PatchAck | BondEditAck | EditApplied | OptimiseResult, transform: Pose | null = null): SessionState {
  const to = new Map(moves.map((move) => [move.id, move.position]));
  return {
    ...base,
    transform: transform ?? base.transform,
    version: ack.version,
    can_undo: ack.can_undo,
    can_redo: ack.can_redo,
    saved_molecule_id: ack.saved_molecule_id,
    atoms: base.atoms.map((atom) => {
      const position = to.get(atom.id);
      return position ? { ...atom, position } : atom;
    }),
  };
}
