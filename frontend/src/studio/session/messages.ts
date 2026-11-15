import type {Vec3} from "../../api/types/structure";

/**
 * The version of the session protocol this client is written for. It goes in every message, and the server refuses
 * a message that names another, so it is raised whenever a message changes in a way an older server or client would
 * misread. The server raises its own to match.
 */
export const PROTOCOL_VERSION = 7;

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
  order: "SINGLE" | "DOUBLE" | "TRIPLE" | "QUADRUPLE" | "AROMATIC";
  aromatic: boolean;
}

/** One disconnected part of a session's molecule. */
export interface SessionFragment {
  key: string;
  name: string;
  atoms: string[];
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
  | LockAck
  | EditApplied
  | GeometryResult
  | SocketError
  | Pong;
