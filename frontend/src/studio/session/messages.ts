import type { Vec3 } from "../../api/types/structure";

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
  pinned: string[];
  locked: string[];
  strain: number;
  /** Whether there is an edit to take back. */
  can_undo: boolean;
  /** Whether there is an undone edit to make again. */
  can_redo: boolean;
}

/** The answer to an atom patch the session applied. */
export interface PatchAck extends ServerEnvelope {
  type: "atom.patch.ack";
  /** The version the session is at after the patch. */
  version: number;
  status: "applied";
  can_undo: boolean;
  can_redo: boolean;
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

export type ServerMessage = SessionReady | SessionState | SessionConflict | PatchAck | SocketError | Pong;
