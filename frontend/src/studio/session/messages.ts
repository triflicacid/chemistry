import type {Vec3} from "../../api/types/structure";

/**
 * The version of the session protocol this client is written for. It goes in every message, and the server refuses
 * a message that names another, so it is raised whenever a message changes in a way an older server or client would
 * misread. The server raises its own to match.
 */
export const PROTOCOL_VERSION = 20;

/** A message the server sent. Every one carries a type; a reply carries the sequence it answers. */
export interface ServerEnvelope {
  type: string;
  sequence?: number;
}

/** The greeting a new connection receives. */
export interface SessionReady extends ServerEnvelope {
  type: "session.ready";
  connection_id: string;
  protocol_version: number;
  max_message_bytes: number;
}

/** A rotation as a quaternion. A unit quaternion when it describes a pose. */
export interface Rotation {
  x: number;
  y: number;
  z: number;
  w: number;
}

/**
 * Where the whole molecule sits in the scene.
 * A point at `local` in the molecule is at `rotation.rotate(local) + position` in the scene.
 */
export interface Pose {
  /** Where the molecule's origin is, in angstroms. */
  position: Vec3;
  rotation: Rotation;
}

/** One atom of a session's working graph. */
export interface SessionAtom {
  id: string;
  element: string;
  /** Mass number, or 0 if the atom has none of its own. */
  isotope: number;
  charge: number;
  aromatic: boolean;
  /** In angstroms. */
  position: Vec3;
  /** Hex colour. */
  colour: string;
  /** Van der Waals radius in angstroms. */
  radius: number;
  /** Whether this is a hydrogen the source molecule counted rather than wrote. */
  was_implicit: boolean;
}

/** One bond of a session's working graph, between two of its atoms. */
export interface SessionBond {
  id: string;
  first: string;
  second: string;
  order: BondOrder;
  aromatic: boolean;
}

/** One disconnected part of a session's molecule. */
export interface SessionFragment {
  key: string;
  name: string;
  atoms: string[];
  smiles: string;
  canonical_smiles: string | null;
  formula: SessionFormula;
}

/** One part's formulae. */
export interface SessionFormula {
  /** Every atom counted together, e.g. `C₂H₆O`. */
  molecular: string;
  /** The molecular formula in its simplest whole-number ratio, e.g. `CH₂O` for glucose. */
  empirical: string;
  /** The condensed structural formula, e.g. `CH₃CH₂OH`, or null if it could not be written. */
  condensed: string | null;
}

/** Remember the viewer's camera. */
export interface SessionCamera {
  /** Where the camera is in the scene, in ångströms. */
  position: Vec3;
  /** The point it looks at and orbits, in ångströms. */
  target: Vec3;
  /** Its vertical field of view, in degrees. */
  fov: number;
  /** How far the picture is turned about the way the camera looks, from level, in degrees. */
  roll: number;
}

/** Everything needed to draw a session. */
export interface SessionState extends ServerEnvelope {
  type: "session.state";
  session_id: string;
  version: number;
  source_molecule_id: string;
  saved_molecule_id: string | null;
  /** The molecule as a chemist would write it, which does not name every atom in `atoms`. */
  smiles: string;
  atoms: SessionAtom[];
  bonds: SessionBond[];
  fragments: SessionFragment[];
  locked: string[];
  /** Where the molecule sits in the scene. The atoms' positions are in the molecule's own frame, not this one. */
  transform: Pose;
  /** Where the camera was when the molecule was last saved, or null if it never was. */
  camera: SessionCamera | null;
  /** Whether there is an edit to take back. */
  can_undo: boolean;
  /** Whether there is an undone edit to make again. */
  can_redo: boolean;
}

/** An atom and where it is to go. */
export interface AtomMove {
  id: string;
  position: Vec3;
}

/** The answer to an atom patch the session applied. */
export interface PatchAck extends ServerEnvelope {
  type: "atom.patch.ack";
  /** The version the session is at after the patch. */
  version: number;
  status: "applied";
  can_undo: boolean;
  can_redo: boolean;
  /** The stored molecule holding the session's work, which this edit may have just created. */
  saved_molecule_id: string | null;
}

/**
 * A change that a handle drags: a bond's length, its torsion between two atoms bonded one to each end, or the angle at
 * an atom between two of its neighbours. The value is where the drag has got to, not how far it has come, so a report
 * can be sent again without effect.
 */
export type BondEdit =
  | {
      kind: "length";
      bond: string;
      /** The length, in angstroms. */
      value: number;
      /** The atom of the bond to leave where it is, or null to hold neither. */
      hold: string | null;
    }
  | {
      kind: "torsion";
      bond: string;
      /** The atom bonded to the bond's first atom that the torsion is measured from. */
      from: string;
      /** The atom bonded to the bond's second atom that the torsion is measured to. */
      to: string;
      /** The torsion, in degrees. */
      value: number;
      hold: string | null;
    }
  | {
      kind: "angle";
      /** The atom the angle is at. */
      centre: string;
      /** One of the two atoms bonded to the centre that the angle is measured between. */
      from: string;
      /** The other. */
      to: string;
      /** The angle, in degrees. */
      value: number;
      /** `from` or `to`, whose side stays where it is, or null to hold neither. */
      hold: string | null;
    };

/** The answer to a change of a bond's length or torsion, or of an angle, that the session applied. */
export interface BondEditAck extends ServerEnvelope {
  type: "bond.length.ack" | "bond.torsion.ack" | "atom.angle.ack";
  /** The version the session is at after the change, which is the one before it if nothing needed to move. */
  version: number;
  status: "applied";
  can_undo: boolean;
  can_redo: boolean;
  /** The stored molecule holding the session's work, which this edit may have just created. */
  saved_molecule_id: string | null;
  /** Where each atom the change moved now is. */
  atoms: { id: string; position: Vec3 }[];
}

/** What to change about an atom. */
export interface AtomChange {
  element?: string;
  charge?: number;
  isotope?: number;
}

/** A bond's order. */
export type BondOrder = "SINGLE" | "DOUBLE" | "TRIPLE" | "QUADRUPLE" | "AROMATIC";

/**
 * Where a new atom goes: bonded to an atom, which the session places it for unless a position is given, or free at a
 * position in the molecule's own frame.
 */
export type AtomPlacement = { attach: string; position?: Vec3 } | { position: Vec3 };

export interface GraphEditAck extends ServerEnvelope {
  type: "atom.set.ack" | "atom.add.ack" | "atom.delete.ack" | "atom.fill.ack" | "bond.add.ack" | "bond.delete.ack" | "bond.order.ack";
  /** The version the session is at after the change, which is the one before it if nothing needed to change. */
  version: number;
  status: "applied";
  added_atoms: string[];
  added_bonds: string[];
  state: SessionState;
}

/** The answer to a move of the molecule as a whole that the session applied. */
export interface TransformAck extends ServerEnvelope {
  type: "molecule.transform.ack";
  /** The version the session is at after the change. */
  version: number;
  status: "applied";
  can_undo: boolean;
  can_redo: boolean;
  /** The stored molecule holding the session's work, which this edit may have just created. */
  saved_molecule_id: string | null;
}

/**
 * The answer to a mutation made on a version the session has moved on from. The client adopts
 * the enclosed state and does not send the refused change again.
 */
export interface SessionConflict extends ServerEnvelope {
  type: "conflict";
  /** The version the session is at now. */
  expected_version: number;
  /** The version the refused message said it was based on. */
  base_version: number;
  state: SessionState;
}

/** The answer to a camera the session kept. */
export interface CameraAck extends ServerEnvelope {
  type: "camera.set.ack";
  /** The stored molecule holding the session's work, which keeping the camera may have just created. */
  saved_molecule_id: string | null;
}

/** The answer to a lock or unlock the session made. */
export interface LockAck extends ServerEnvelope {
  type: "atom.lock.ack";
  /** The version the session is at after the change. */
  version: number;
  /** Every atom and bond that is now locked, not only the ones this change touched. */
  locked: string[];
  /** The stored molecule holding the session's work, which this change may have just created. */
  saved_molecule_id: string | null;
}

/** One edit in the history. */
export interface HistoryEntry {
  /** What the edit did. */
  label: string;
  /** When it was made, in milliseconds since the epoch. */
  at: number;
}

/** The session's edits, which a point in can be returned to. */
export interface EditHistory extends ServerEnvelope {
  type: "history.list";
  version: number;
  /** How many of the edits are in effect; the ones after it have been undone. */
  position: number;
  /** The edits, the earliest first. */
  entries: HistoryEntry[];
}

/** The answer to an undo or redo: only what it changed, not the whole state. */
export interface EditApplied extends ServerEnvelope {
  type: "edit.applied";
  /** The version the session is at after the step. */
  version: number;
  can_undo: boolean;
  can_redo: boolean;
  saved_molecule_id: string | null;
  /** Where each atom the step touched is now. */
  atoms: { id: string; position: Vec3 }[];
  /** Where the molecule now sits, or null if the step did not move it as a whole. */
  transform: Pose | null;
  /** The whole state, if the step changed the graph and so left the atoms' attributes and the SMILES out of date; otherwise null. */
  state: SessionState | null;
}

/** The answer to a request to optimise, saying only that the optimisation has begun; progress and a result follow. */
export interface OptimiseStarted extends ServerEnvelope {
  type: "optimise.started";
  operation_id: string;
  /** The version of the session the optimisation is made from. */
  version: number;
}

/** How an optimisation is getting on, sent on the server's own initiative at most about ten times a second. */
export interface OptimiseProgress extends ServerEnvelope {
  type: "optimise.progress";
  session_id: string;
  operation_id: string;
  /** How many steps have been taken. */
  iteration: number;
  /** The value being minimised, which has no unit and is a sign of progress only: it is not the strain shown in the toolbar. */
  energy: number;
}

/** The value an optimisation was minimising after some number of steps. */
export interface EnergyStep {
  /** How many steps had been taken, which is zero for the geometry it started from. */
  iteration: number;
  /** The value, which has no unit. */
  energy: number;
}

/** How an optimisation ended, sent on the server's own initiative. */
export interface OptimiseResult extends ServerEnvelope {
  type: "optimise.result";
  session_id: string;
  operation_id: string;
  /** The version of the session the optimisation was made from. */
  base_version: number;
  /**
   * `complete` moved atoms and is one edit that can be undone; `unchanged` found nothing worth moving; `failed` could
   * not reach a relaxed structure; `cancelled` was stopped or overtaken by an edit. Only `complete` changes the session.
   */
  status: "complete" | "unchanged" | "failed" | "cancelled";
  /** The version the session is at after it. */
  version: number;
  can_undo: boolean;
  can_redo: boolean;
  saved_molecule_id: string | null;
  /** Where each atom that moved now is, empty unless the status is `complete`. */
  atoms: { id: string; position: Vec3 }[];
  /**
   * The strain of the geometry it started from and the session ends with, measured as `geometry.result` measures it,
   * so they can be set beside the toolbar's figure. Null if it never got that far or the geometry could not be examined.
   */
  strain_before: number | null;
  strain_after: number | null;
  iterations: number;
  /** The value being minimised at every step, from the start, which the progress reports sample. Empty if it never began. */
  energy_history: EnergyStep[];
  /** Things the optimisation could not do as asked, such as stereochemistry it could not read. */
  warnings: string[];
  /** Why it failed or was cancelled. */
  message: string | null;
}

/** The answer to a request to stop optimising. */
export interface OptimiseCancelAck extends ServerEnvelope {
  type: "optimise.cancel.ack";
  /** Whether there was an optimisation to stop, which then ends with a `cancelled` result. */
  cancelling: boolean;
}

/** The answer to a request for charges, which says only that the request was understood: the charges follow as a `charges.result`. */
export interface ChargesRequestAck extends ServerEnvelope {
  type: "charges.request.ack";
}

/** One atom's partial charge. */
export interface AtomCharge {
  id: string;
  /** In elementary charges. */
  charge: number;
}

/** One Fock build of a self-consistent-field calculation. */
export interface ScfStep {
  /** How many had been built, from 1. */
  iteration: number;
  /** The total energy so far, in hartree. */
  energy: number;
  /** The root-mean-square element of the Fock-density commutator, which is zero at self-consistency. */
  error: number;
}

/**
 * The partial charge of every atom, sent on the server's own initiative once it has worked them out after a request. It
 * describes the geometry at `version`, so it is out of date once the session has moved on from that.
 */
export interface ChargesResult extends ServerEnvelope {
  type: "charges.result";
  session_id: string;
  version: number;
  /** The model the client asked for, which is not always the one that assigned the charges. */
  requested: string;
  status: "complete" | "failed";
  /** The name of the model that assigned the charges; null if the calculation failed. */
  model: string | null;
  /** What the charges mean, such as `MULLIKEN`, or `FORMAL` where no real model could be applied; null if the calculation failed. */
  convention: string | null;
  /** Each atom's charge, empty if the calculation failed. */
  charges: AtomCharge[];
  /** Every Fock build of the quantum calculation, in order; empty if there was none, as for formal charges. */
  convergence: ScfStep[];
  /** How long the calculation took, in milliseconds. */
  elapsed_ms: number;
  /** Why the calculation failed, if it did. */
  message: string | null;
}

/**
 * How the calculation of the charges is getting on, sent on the server's own initiative while it runs, and dropped if the
 * session moves on first.
 */
export interface ChargesProgress extends ServerEnvelope {
  type: "charges.progress";
  session_id: string;
  version: number;
  /** The model the client asked for. */
  requested: string;
  /** `starting`, then `integrals` while the integrals are worked out, then `iterating` while the orbitals are made self-consistent. */
  stage: "starting" | "integrals" | "iterating";
  /** How many Fock builds have been made, or zero if none has. */
  iteration: number;
  /** The total energy so far in hartree, or null before the first Fock build. */
  energy: number | null;
  /** How far the orbitals are from self-consistent, or null before the first Fock build. */
  error: number | null;
  /** How long the calculation has been running, in milliseconds. */
  elapsed_ms: number;
}

/** The answer to a request for forces, which says only that the request was understood: the forces follow as a `forces.result`. */
export interface ForcesRequestAck extends ServerEnvelope {
  type: "forces.request.ack";
}

/** The force on one atom. */
export interface AtomForce {
  id: string;
  /** In hartree per bohr. */
  force: Vec3;
}

/** The total force and torque on one molecule of the session. */
export interface FragmentForce {
  atoms: string[];
  /** The mean position of the molecule's atoms in the session's frame, in angstroms, which the torque is about. */
  centre: Vec3;
  /** In hartree per bohr. */
  force: Vec3;
  /** In hartree. */
  torque: Vec3;
}

/** The energy between two molecules of the session. */
export interface PairEnergy {
  /** An atom of the first molecule, which names it. */
  first: string;
  /** An atom of the second. */
  second: string;
  /** What the energy is made of. */
  terms: EnergyTerms;
  /** The interaction energy of the two in hartree, which is the sum of the terms. */
  energy: number;
}

/**
 * The forces the electron-density force field puts on the atoms of a session from the other molecules in it, sent on the
 * server's own initiative after a request. They describe the geometry at `version`, so they are out of date once the
 * session has moved on from that.
 */
export interface ForcesResult extends ServerEnvelope {
  type: "forces.result";
  session_id: string;
  version: number;
  /** `unavailable` if the session is one molecule, which has nothing acting on it from outside. */
  status: "complete" | "unavailable" | "failed";
  /** The parameterisation of the force field, such as `ECP`. */
  model: string | null;
  /** The total intermolecular energy in hartree. */
  energy: number | null;
  atoms: AtomForce[];
  fragments: FragmentForce[];
  /** The energy between each pair of molecules, split into its parts. */
  interactions: PairEnergy[];
  /** How long the calculation took, in milliseconds. */
  elapsed_ms: number;
  /** Why the forces are unavailable or could not be calculated. */
  message: string | null;
}

/** The answer to a request for a scan, which says only that the request was understood: the scan follows as a `scan.result`. */
export interface ScanRequestAck extends ServerEnvelope {
  type: "scan.request.ack";
}

/** The parts of an interaction energy, in hartree. */
export interface EnergyTerms {
  nuclear_nuclear: number;
  nuclear_electron: number;
  electron_electron: number;
  exchange_correlation: number;
}

/** The interaction energy of two molecules with the hydrogen bond between them at one length. */
export interface ScanPoint {
  /** The distance from the hydrogen to the acceptor, in angstroms. */
  distance: number;
  /** The interaction energy of the two molecules, in hartree. */
  energy: number;
  /**
   * The interaction energy of the moved molecule with every other molecule, in hartree, which counts a third molecule that
   * the move brings closer or takes away. Null if there are only two molecules, when it is the same as `energy`.
   */
  energy_with_others: number | null;
  /** What `energy` is made of. */
  terms: EnergyTerms;
  /** Where the atoms of the molecule that is moved are at this distance, in the session's frame. */
  atoms: { id: string; position: Vec3 }[];
}

/** One hydrogen bond, scanned. */
export interface ScanBond {
  /** The heavy atom that gives the hydrogen. */
  donor: string;
  hydrogen: string;
  /** The atom that receives the hydrogen. */
  acceptor: string;
  /** How far the hydrogen is from the acceptor as the geometry is, in angstroms. */
  current: number;
  /** The scan, in order of increasing distance. Every bond is scanned at the same distances. */
  points: ScanPoint[];
  /** The distance of the lowest energy between the two molecules, in angstroms, refined between points. */
  minimum_distance: number;
  /** The interaction energy there, in hartree. */
  minimum_energy: number;
}

/**
 * How the energy between a session's molecules changes as one is moved along each of their closest hydrogen bonds, sent on
 * the server's own initiative after a request. It describes the geometry at `version`.
 */
export interface ScanResult extends ServerEnvelope {
  type: "scan.result";
  session_id: string;
  version: number;
  /** `unavailable` if the session has no pair of molecules with a hydrogen bond between them to scan. */
  status: "complete" | "unavailable" | "failed";
  /** How many separate molecules the session has. */
  molecules: number;
  /** The bonds scanned, the one whose hydrogen is nearest its acceptor first; empty if there is no scan. */
  bonds: ScanBond[];
  model: string | null;
  elapsed_ms: number;
  /** Why there is no scan. */
  message: string | null;
}

/** One term of the geometry beyond its tolerance. */
export interface GeometryFinding {
  kind: "bond" | "angle" | "clash";
  /** The atoms it is between, in order. */
  atoms: string[];
  /** Angstroms for a bond or a clash, degrees for an angle. */
  actual: number;
  /** What it should be, in the same unit. */
  ideal: number;
  /** How many tolerances it strays by. */
  severity: number;
}

/** Two atoms closer than they should be. */
export interface GeometryClash {
  first: string;
  second: string;
  /** Angstroms. */
  distance: number;
  /** The least distance they should keep, in angstroms. */
  contact: number;
}

/** What one atom contributes to the strain. */
export interface AtomStrain {
  id: string;
  strain: number;
}

/**
 * How strained the geometry of a session is, sent on the server's own initiative once the session has been left
 * alone. It describes the geometry at `version`, so it is out of date once the session has moved on from that.
 */
export interface GeometryResult extends ServerEnvelope {
  type: "geometry.result";
  session_id: string;
  version: number;
  status: "complete" | "failed";
  /** The total strain, where zero is a shape with nothing wrong with it; null if the calculation failed. */
  strain: number | null;
  /** The worst terms beyond their tolerance, worst first. */
  findings: GeometryFinding[];
  clashes: GeometryClash[];
  /** Each strained atom's share of the strain. */
  atom_strain: AtomStrain[];
  /** Why the calculation failed, if it did. */
  message: string | null;
}

/** A message the server could not handle, which leaves the connection open. */
export interface SocketError extends ServerEnvelope {
  type: "error";
  /** The type of the message that failed, if the server could read it. */
  answering?: string;
  error: { code: string; message: string };
}

/** The answer to a ping. */
export interface Pong extends ServerEnvelope {
  type: "pong";
}

export type ServerMessage =
  | SessionReady
  | SessionState
  | SessionConflict
  | PatchAck
  | TransformAck
  | BondEditAck
  | GraphEditAck
  | LockAck
  | CameraAck
  | EditApplied
  | OptimiseStarted
  | OptimiseProgress
  | OptimiseResult
  | OptimiseCancelAck
  | GeometryResult
  | ChargesRequestAck
  | ChargesProgress
  | ChargesResult
  | ForcesRequestAck
  | ForcesResult
  | ScanRequestAck
  | ScanResult
  | SocketError
  | Pong;
