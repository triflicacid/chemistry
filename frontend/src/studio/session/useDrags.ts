import {useCallback, useRef} from "react";
import type {Vec3} from "../../api/types/structure";
import type {AtomMove, BondEdit, Pose} from "./messages";
import {samePose} from "./pose";
import type {EditResult} from "./types";
import type {Edits} from "./useEdits";
import type {SessionLink} from "./useSocket";

export const DRAG_INTERVAL_MS = 100;

/** Something being dragged. */
interface Drag {
  /** What is in the drag, so that a report about something different starts a new drag. */
  key: string;
  /** Where the pointer last put it: a list of atom moves, or a pose. */
  latest: unknown;
  /** What the last patch sent, so that one that would repeat it is not. */
  sent: unknown;
  /** Whether two reports put it in the same place. */
  same: (first: never, second: never) => boolean;
  /** Sends a report to the session as part of the drag. */
  transmit: (report: never) => void;
  /** When the last patch was sent. */
  at: number;
  timer: ReturnType<typeof setTimeout> | undefined;
  /** Set when the drag ends, so that patches still waiting their turn are dropped. */
  closed: boolean;
}

/** The drags of atoms, a bond and the molecule, which report as they go and are sent as one edit when dropped. */
export interface Drags {
  dragBond: (edit: BondEdit) => void;
  dropBond: (edit: BondEdit) => Promise<EditResult>;
  cancelBondDrag: () => Promise<EditResult>;
  dragAtoms: (moves: AtomMove[]) => void;
  dropAtoms: (moves: AtomMove[]) => Promise<EditResult>;
  cancelDrag: (moves: AtomMove[]) => Promise<EditResult>;
  dragMolecule: (pose: Pose) => void;
  dropMolecule: (pose: Pose) => Promise<EditResult>;
  cancelMoleculeDrag: (from: Pose) => Promise<EditResult>;
}

/** Spaces out the changes a drag sends to the session, and puts things back if it is abandoned. */
export function useDrags({ held }: SessionLink, { send, sendBond, sendPose }: Edits): Drags {
  const drag = useRef<Drag | null>(null);

  /**
   * Reports where something being dragged has got to.
   *
   * @param key what is being dragged, so that a report about something else starts a new drag
   * @param report where it is now
   * @param same whether two reports put it in the same place
   * @param transmit sends a report to the session, as part of the drag
   */
  const reporting = useCallback(<T,>(key: string, report: T, same: (first: T, second: T) => boolean, transmit: (report: T) => void) => {
    let current = drag.current;
    if (!current || current.key !== key) {
      current = { key, latest: report, sent: null, same, transmit, at: 0, timer: undefined, closed: false } as Drag;
      drag.current = current;
    }
    current.latest = report;

    if (current.timer !== undefined) {
      return;
    }

    const live = current;
    const wait = Math.max(0, live.at + DRAG_INTERVAL_MS - Date.now());
    live.timer = setTimeout(() => {
      live.timer = undefined;
      live.at = Date.now();
      if (live.closed || (live.sent !== null && (live.same as (a: unknown, b: unknown) => boolean)(live.sent, live.latest))) {
        return;
      }
      live.sent = live.latest;
      (live.transmit as (report: unknown) => void)(live.latest);
    }, wait);
  }, []);

  const dragAtoms = useCallback(
    (moves: AtomMove[]) => reporting(`atoms:${moves.map((move) => move.id).join(",")}`, moves, sameMoves, (latest) => void send(latest, false)),
    [reporting, send],
  );

  const dragMolecule = useCallback((pose: Pose) => reporting("molecule", pose, samePose, (latest) => void sendPose(latest, false)), [reporting, sendPose]);

  /** Where every atom was when the drag of a bond began, which cancelling it puts back, or null while none is under way. */
  const bondOrigin = useRef<Map<string, Vec3> | null>(null);

  const dragBond = useCallback(
    (edit: BondEdit) => {
      bondOrigin.current ??= new Map((held.current?.atoms ?? []).map((atom) => [atom.id, atom.position]));
      reporting(`bond:${edit.kind}:${edit.kind === "angle" ? edit.centre : edit.bond}`, edit, (first, second) => first.value === second.value && first.hold === second.hold, (latest) => void sendBond(latest, false));
    },
    [held, reporting, sendBond],
  );

  /** Ends the drag in progress, if there is one, so that nothing it still had waiting is sent. */
  const closeDrag = useCallback((): Drag | null => {
    const current = drag.current;
    drag.current = null;
    if (current) {
      clearTimeout(current.timer);
      current.closed = true;
    }
    return current;
  }, []);

  const dropAtoms = useCallback(
    (moves: AtomMove[]) => {
      closeDrag();
      return send(moves, true);
    },
    [closeDrag, send],
  );

  const cancelDrag = useCallback(
    async (from: AtomMove[]): Promise<EditResult> => {
      const ended = closeDrag();

      // a drag that had sent nothing has nothing to put back. One that had is put back by a patch that
      // commits, which records nothing to undo because the atoms end where they began
      return ended?.sent ? send(from, true) : { kind: "applied" };
    },
    [closeDrag, send],
  );

  const dropBond = useCallback(
    (edit: BondEdit) => {
      closeDrag();
      bondOrigin.current = null;
      return sendBond(edit, true);
    },
    [closeDrag, sendBond],
  );

  const cancelBondDrag = useCallback(async (): Promise<EditResult> => {
    const ended = closeDrag();
    const origin = bondOrigin.current;
    bondOrigin.current = null;
    if (!ended?.sent || !origin) {
      return { kind: "applied" };
    }

    // the atoms are put back by a patch that commits, which records nothing to undo because they end where they began
    const back = (held.current?.atoms ?? []).filter((atom) => {
      const was = origin.get(atom.id);
      return was !== undefined && (was.x !== atom.position.x || was.y !== atom.position.y || was.z !== atom.position.z);
    });
    return back.length === 0 ? { kind: "applied" } : send(back.map((atom) => ({ id: atom.id, position: origin.get(atom.id) as Vec3 })), true);
  }, [closeDrag, held, send]);

  const dropMolecule = useCallback(
    (pose: Pose) => {
      closeDrag();
      return sendPose(pose, true);
    },
    [closeDrag, sendPose],
  );

  const cancelMoleculeDrag = useCallback(
    async (from: Pose): Promise<EditResult> => {
      const ended = closeDrag();

      // as with atoms: only a drag that had sent something has anything to put back
      return ended?.sent ? sendPose(from, true) : { kind: "applied" };
    },
    [closeDrag, sendPose],
  );

  return { dragBond, dropBond, cancelBondDrag, dragAtoms, dropAtoms, cancelDrag, dragMolecule, dropMolecule, cancelMoleculeDrag };
}

/** Whether two reports put the same atoms in the same places. */
function sameMoves(first: AtomMove[], second: AtomMove[]): boolean {
  return first.every((move, i) => {
    const other = second[i];
    return other !== undefined && move.position.x === other.position.x && move.position.y === other.position.y && move.position.z === other.position.z;
  });
}
