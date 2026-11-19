import {useMemo, useRef} from "react";
import {ChemistryApi} from "../../api/ChemistryApi";
import type {Vec3} from "../../api/types/structure";
import type {
    AtomChange,
    AtomMove,
    AtomPlacement,
    BondEdit,
    BondOrder,
    GeometryResult,
    HistoryEntry,
    Pose,
    SessionCamera,
    SessionState
} from "./messages";
import {useCameraSave} from "./camera";
import type {Activity, EditResult, OptimisationOutcome, Optimising, SessionPhase, SubmitResult} from "./types";
import {useDrags} from "./useDrags";
import {useEdits} from "./useEdits";
import {useOptimise} from "./useOptimise";
import {type SocketEvents, useSocket} from "./useSocket";

export type {Activity, EditResult, OptimisationOutcome, Optimising, SessionPhase, SubmitResult} from "./types";

export interface Session {
  phase: SessionPhase;
  state: SessionState | null;
  /** The optimisation under way, or null if there is none. */
  optimising: Optimising | null;
  /** How the latest optimisation ended, or null if there has been none in this session. */
  outcome: OptimisationOutcome | null;
  /**
   * How strained the geometry is, as the server last said. It describes the version in `version`, which is out of date
   * once the session has moved on; the server sends a fresh one shortly after every change.
   */
  geometry: GeometryResult | null;
  activity: Activity[];
  /** Stores a molecule written as SMILES and opens a session on it. */
  openSmiles: (smiles: string) => void;
  /** Rebuilds the open session's structure from SMILES text. */
  submitSmiles: (smiles: string) => Promise<SubmitResult>;
  /** Puts an atom at a position, as one edit that can be undone. */
  moveAtom: (id: string, position: Vec3) => Promise<EditResult>;
  /** Changes an atom's non-geometric properties. */
  setAtom: (id: string, change: AtomChange) => Promise<EditResult>;
  /** Adds an atom, bonded to an atom (which the server places it for, a bond length away) or free at a position, as one edit that can be undone. */
  addAtom: (element: string, placement: AtomPlacement, order: BondOrder) => Promise<EditResult>;
  /** Bonds two atoms where they stand, as one edit that can be undone. */
  addBond: (first: string, second: string, order: BondOrder) => Promise<EditResult>;
  /** Gives a list of bonds an order. */
  setBondOrder: (bonds: string[], order: BondOrder) => Promise<EditResult>;
  /** Deletes atoms and their bonds, with the hydrogens bonded only to them if asked, as one edit that can be undone. */
  deleteAtoms: (ids: string[], hydrogens: boolean) => Promise<EditResult>;
  /** Gives atoms the hydrogens that fill their valence. */
  fillValences: (ids: string[]) => Promise<EditResult>;
  /** Deletes bonds and leaves their atoms, as one edit that can be undone. */
  deleteBonds: (ids: string[]) => Promise<EditResult>;
  /** Gives a bond a length. */
  setBondLength: (bond: string, length: number, hold: string | null) => Promise<EditResult>;
  /** Gives a bond a torsion by turning one side of it, as one edit that can be undone. The server refuses ring bonds. */
  setBondTorsion: (bond: string, from: string, to: string, angle: number, hold: string | null) => Promise<EditResult>;
  /** Gives an atom's two bonds an angle by swinging one of them, as one edit that can be undone. The server refuses ring bonds. */
  setAngle: (centre: string, from: string, to: string, angle: number, hold: string | null) => Promise<EditResult>;
  /** Reports where a handle dragging a bond has got to. */
  dragBond: (edit: BondEdit) => void;
  /** Ends a drag of a bond at a value, as one edit that can be undone however many changes the drag sent. */
  dropBond: (edit: BondEdit) => Promise<EditResult>;
  /** Abandons a drag of a bond, putting every atom the drag moved back where it was picked up. */
  cancelBondDrag: () => Promise<EditResult>;
  /** Reports where dragged atoms have got to. */
  dragAtoms: (moves: AtomMove[]) => void;
  /** Ends a drag at positions, as one edit that can be undone however many patches the drag sent. */
  dropAtoms: (moves: AtomMove[]) => Promise<EditResult>;
  /** Abandons a drag, putting the atoms back at the positions they were picked up from. */
  cancelDrag: (moves: AtomMove[]) => Promise<EditResult>;
  /** Puts the molecule somewhere in the scene, as one edit that can be undone. */
  moveMolecule: (pose: Pose) => Promise<EditResult>;
  /** Reports where a dragged molecule has got to. */
  dragMolecule: (pose: Pose) => void;
  /** Ends a drag of the molecule at a pose, as one edit that can be undone however many patches the drag sent. */
  dropMolecule: (pose: Pose) => Promise<EditResult>;
  /** Abandons a drag of the molecule, putting it back at the pose it was picked up at. */
  cancelMoleculeDrag: (from: Pose) => Promise<EditResult>;
  /**
   * Says where the camera is, to be kept with the molecule so that opening it again shows the same view. Sends once the
   * camera has been left alone for {@link CAMERA_SAVE_MS}, with the latest. It is not an edit, so it cannot be undone and
   * the version does not change, but the first one may store the molecule, which is how a reload finds the view.
   */
  saveCamera: (camera: SessionCamera) => void;
  /** Locks or unlocks atoms, so that they refuse or accept edits. A lock is saved with the molecule but is not an edit, so it cannot be undone. */
  lockAtoms: (ids: string[], locked: boolean) => Promise<EditResult>;
  /**
   * Relaxes the geometry on the server, holding locked atoms where they are. Resolves once the server has started,
   * not when it has finished: the result is applied as one edit that can be undone, and reported in the activity log.
   */
  optimise: () => Promise<EditResult>;
  /** Asks the server to stop the optimisation under way, which then ends without changing the structure. */
  cancelOptimise: () => Promise<void>;
  /** Takes back the latest edit. */
  undo: () => Promise<EditResult>;
  /** Makes the latest undone edit again. */
  redo: () => Promise<EditResult>;
  /** Lists the edits made, or null if they could not be listed. */
  history: () => Promise<{ entries: HistoryEntry[]; position: number } | null>;
  /**
   * Moves to a point in the history, by having that many edits in effect.
   *
   * @param position how many edits to have in effect
   * @param discard whether to forget the edits after it, rather than keep them to redo
   */
  goTo: (position: number, discard: boolean) => Promise<EditResult>;
  /** Forgets every edit, leaving the structure as it is. */
  clearHistory: () => Promise<EditResult>;
}

/**
 * Opens whatever the URL asks for and offers everything that can be done to it, by composing the hooks that each own one
 * part: the connection, the edits, the drags, the optimisation and the camera.
 */
export function useSession(client?: ChemistryApi): Session {
  // a client built fresh each render would change the effect's dependencies every render,
  // which would close the socket and open another one on every keystroke
  const api = useMemo(() => client ?? new ChemistryApi(), [client]);

  const events = useRef<SocketEvents>({ reset: () => {}, dropped: () => {} });
  const socket = useSocket(api, events);
  const edits = useEdits(socket);
  const drags = useDrags(socket, edits);
  const { optimising, outcome, optimise, cancelOptimise } = useOptimise(socket, edits, events);
  const saveCamera = useCameraSave(socket);

  const { phase, state, geometry, activity, openSmiles } = socket;
  const { moveAtom, setAtom, addAtom, addBond, setBondOrder, deleteAtoms, fillValences, deleteBonds, setBondLength, setBondTorsion, setAngle, moveMolecule, lockAtoms, undo, redo, history, goTo, clearHistory, submitSmiles } = edits;

  return {
    phase,
    state,
    optimising,
    outcome,
    geometry,
    activity,
    openSmiles,
    submitSmiles,
    moveAtom,
    setAtom,
    addAtom,
    addBond,
    setBondOrder,
    deleteAtoms,
    fillValences,
    deleteBonds,
    setBondLength,
    setBondTorsion,
    setAngle,
    ...drags,
    moveMolecule,
    lockAtoms,
    saveCamera,
    optimise,
    cancelOptimise,
    undo,
    redo,
    history,
    goTo,
    clearHistory,
  };
}
