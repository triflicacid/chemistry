import {useCallback, useRef} from "react";
import type {Vec3} from "../../api/types/structure";
import {describingSmiles} from "./location";
import type {AtomMove, BondEdit, BondEditAck, HistoryEntry, Pose, SessionConflict, SessionState} from "./messages";
import {applyAck} from "./acks";
import {reason} from "./errors";
import type {StudioSocket} from "./studioSocket";
import type {EditResult, SubmitResult} from "./types";
import type {SessionLink} from "./useSocket";

/** The edits of the structure, each sent to the session on the version the one before left. */
export interface Edits {
  serially: <T>(task: () => Promise<T>) => Promise<T>;

  send: (moves: AtomMove[], commit: boolean) => Promise<EditResult>;
  sendBond: (edit: BondEdit, commit: boolean) => Promise<EditResult>;
  sendPose: (pose: Pose, commit: boolean) => Promise<EditResult>;
  submitSmiles: (smiles: string) => Promise<SubmitResult>;
  moveAtom: (id: string, position: Vec3) => Promise<EditResult>;
  setBondLength: (bond: string, length: number, hold: string | null) => Promise<EditResult>;
  setBondTorsion: (bond: string, from: string, to: string, angle: number, hold: string | null) => Promise<EditResult>;
  setAngle: (centre: string, from: string, to: string, angle: number, hold: string | null) => Promise<EditResult>;
  moveMolecule: (pose: Pose) => Promise<EditResult>;
  lockAtoms: (ids: string[], locked: boolean) => Promise<EditResult>;
  undo: () => Promise<EditResult>;
  redo: () => Promise<EditResult>;
  history: () => Promise<{ entries: HistoryEntry[]; position: number } | null>;
  goTo: (position: number, discard: boolean) => Promise<EditResult>;
  clearHistory: () => Promise<EditResult>;
}

/** Sends the edits that change the structure, and the history of them, to the session. */
export function useEdits({ socketRef, held, adopt, note }: SessionLink): Edits {
  const queue = useRef<Promise<unknown>>(Promise.resolve());

  const submitSmiles = useCallback(
    async (smiles: string): Promise<SubmitResult> => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return { kind: "refused", message: "There is no open session to edit." };
      }

      try {
        const reply = await socket.submitSmiles(base.session_id, base.version, smiles);
        if (reply.type === "conflict") {
          adopt(reply.state);
          note("The structure changed since you started editing, so your SMILES was not used", true);
          return { kind: "conflict" };
        }

        adopt(reply);
        describingSmiles(reply.smiles);
        note(`Structure replaced: ${reply.smiles}`);
        return { kind: "replaced" };
      } catch (cause) {
        note(reason(cause), true);
        return { kind: "refused", message: reason(cause) };
      }
    },
    [adopt, held, note, socketRef],
  );

  const serially = useCallback(<T,>(task: () => Promise<T>): Promise<T> => {
    const run = queue.current.then(task, task);
    queue.current = run.catch(() => undefined);
    return run;
  }, []);

  /**
   * Puts atoms at positions, in one patch.`
   *
   * @param moves the atoms and where to put each
   * @param commit whether this ends the edit it belongs to; a patch that does not is part of a drag
   */
  const send = useCallback(
    (moves: AtomMove[], commit: boolean): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.patchAtoms(base.session_id, base.version, moves, commit);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says where the atom is
          if (held.current?.version === base.version) {
            adopt(applyAck(base, moves, reply));
          }
          if (commit) {
            const [first] = moves as [AtomMove, ...AtomMove[]];
            const { x, y, z } = first.position;
            note(moves.length === 1 ? `Moved ${first.id} to ${x.toFixed(3)}, ${y.toFixed(3)}, ${z.toFixed(3)}` : `Moved ${moves.length} atoms`);
          }
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  const moveAtom = useCallback((id: string, position: Vec3) => send([{ id, position }], true), [send]);

  /**
   * Asks the server to reshape a bond.
   *
   * @param ask makes the request, given the socket and the state it is made on
   * @param message what to say in the log when it is done, or null to say nothing, as for part of a drag
   */
  const reshape = useCallback(
    (ask: (socket: StudioSocket, base: SessionState) => Promise<BondEditAck | SessionConflict>, message: string | null): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await ask(socket, base);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says where the atoms are
          if (held.current?.version === base.version) {
            adopt(applyAck(base, reply.atoms, reply));
          }
          if (message !== null) {
            note(message);
          }
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  /**
   * Sends a change to a bond.
   *
   * @param edit what to change it to
   * @param commit whether this ends the edit it belongs to; a change that does not is part of a drag
   */
  const sendBond = useCallback(
    (edit: BondEdit, commit: boolean): Promise<EditResult> => {
      const message = !commit
        ? null
        : edit.kind === "length"
          ? `Set ${edit.bond} to ${edit.value.toFixed(3)} Å`
          : edit.kind === "torsion"
            ? `Turned ${edit.bond} to a torsion of ${edit.value.toFixed(1)}°`
            : `Set the angle at ${edit.centre} to ${edit.value.toFixed(1)}°`;
      return reshape(
        (socket, base) =>
          edit.kind === "length"
            ? socket.setBondLength(base.session_id, base.version, edit.bond, edit.value, edit.hold, commit)
            : edit.kind === "torsion"
              ? socket.setBondTorsion(base.session_id, base.version, edit.bond, edit.from, edit.to, edit.value, edit.hold, commit)
              : socket.setAngle(base.session_id, base.version, edit.centre, edit.from, edit.to, edit.value, edit.hold, commit),
        message,
      );
    },
    [reshape],
  );

  const setBondLength = useCallback((bond: string, length: number, hold: string | null) => sendBond({ kind: "length", bond, value: length, hold }, true), [sendBond]);

  const setAngle = useCallback(
    (centre: string, from: string, to: string, angle: number, hold: string | null) => sendBond({ kind: "angle", centre, from, to, value: angle, hold }, true),
    [sendBond],
  );

  const setBondTorsion = useCallback(
    (bond: string, from: string, to: string, angle: number, hold: string | null) => sendBond({ kind: "torsion", bond, from, to, value: angle, hold }, true),
    [sendBond],
  );

  /**
   * Puts the molecule at a pose.
   *
   * @param pose where to put it
   * @param commit whether this ends the edit it belongs to; a patch that does not is part of a drag
   */
  const sendPose = useCallback(
    (pose: Pose, commit: boolean): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.transformMolecule(base.session_id, base.version, pose, commit);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says where the molecule is
          if (held.current?.version === base.version) {
            adopt({
              ...base,
              version: reply.version,
              can_undo: reply.can_undo,
              can_redo: reply.can_redo,
              saved_molecule_id: reply.saved_molecule_id,
              transform: pose,
            });
          }
          if (commit) {
            const { x, y, z } = pose.position;
            note(`Moved the molecule to ${x.toFixed(3)}, ${y.toFixed(3)}, ${z.toFixed(3)}`);
          }
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  const moveMolecule = useCallback((pose: Pose) => sendPose(pose, true), [sendPose]);

  const lockAtoms = useCallback(
    (ids: string[], locked: boolean): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.lockAtoms(base.session_id, base.version, ids, locked);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says what is locked
          if (held.current?.version === base.version) {
            adopt({ ...base, version: reply.version, locked: reply.locked, saved_molecule_id: reply.saved_molecule_id });
          }
          note(`${locked ? "Locked" : "Unlocked"} ${ids.length === 1 ? ids[0] : `${ids.length} atoms`}`);
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  const step = useCallback(
    (direction: "undo" | "redo"): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.step(direction, base.session_id, base.version);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note(`The structure changed, so the ${direction} was not made`, true);
            return { kind: "conflict" };
          }

          // the answer is only the atoms that moved; a resync that landed meanwhile already says where they are
          if (held.current?.version === base.version) {
            adopt(applyAck(base, reply.atoms, reply, reply.transform));
          }
          note(direction === "undo" ? "Undid the last edit" : "Redid the last undone edit");
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  const undo = useCallback(() => step("undo"), [step]);
  const redo = useCallback(() => step("redo"), [step]);

  const history = useCallback(
    (): Promise<{ entries: HistoryEntry[]; position: number } | null> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return null;
        }

        try {
          const reply = await socket.history(base.session_id, base.version);
          if (reply.type === "conflict") {
            adopt(reply.state);
            return null;
          }
          return { entries: reply.entries, position: reply.position };
        } catch (cause) {
          note(reason(cause), true);
          return null;
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  const goTo = useCallback(
    (position: number, discard: boolean): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.rewind(base.session_id, base.version, position, discard);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed, so the history was not moved", true);
            return { kind: "conflict" };
          }

          if (held.current?.version === base.version) {
            adopt(applyAck(base, reply.atoms, reply, reply.transform));
          }
          note(discard ? `Rewound to edit ${position}, discarding the later edits` : `Went to edit ${position}`);
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  const clearHistory = useCallback(
    (): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.clearHistory(base.session_id, base.version);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed, so the history was not cleared", true);
            return { kind: "conflict" };
          }

          if (held.current?.version === base.version) {
            adopt(applyAck(base, reply.atoms, reply, reply.transform));
          }
          note("Cleared the history");
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  return { serially, send, sendBond, sendPose, submitSmiles, moveAtom, setBondLength, setBondTorsion, setAngle, moveMolecule, lockAtoms, undo, redo, history, goTo, clearHistory };
}
