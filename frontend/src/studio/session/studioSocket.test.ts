import {afterEach, describe, expect, it, vi} from "vitest";
import {StudioError, StudioSocket} from "./studioSocket";
import {
    type GeometryResult,
    type OptimiseProgress,
    type OptimiseResult,
    PROTOCOL_VERSION,
    type SessionAtom,
    type SessionState,
} from "./messages";
import {IDENTITY_POSE} from "./pose";

const STUDIO_URL = process.env.WS_URL ?? "ws://localhost:8081/ws/studio";
const API_URL = process.env.API_URL ?? "http://localhost:8080";

/** How long to wait for the server to answer before giving up. */
const TIMEOUT_MS = 5_000;

/** A message the server sent, as far as this test cares about it. */
interface ServerMessage {
  type: string;
  sequence?: number;
}

/**
 * Opens a connection, sends one message, and resolves with the replies as they arrive.
 *
 * @param send what to send once the connection is open
 * @param wanted how many messages to collect before closing
 */
function exchange(send: unknown, wanted: number): Promise<ServerMessage[]> {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(STUDIO_URL);
    const received: ServerMessage[] = [];

    const timer = setTimeout(() => {
      socket.close();
      reject(new Error(`The server sent ${received.length} of ${wanted} messages within ${TIMEOUT_MS}ms`));
    }, TIMEOUT_MS);

    const finish = (outcome: () => void) => {
      clearTimeout(timer);
      socket.close();
      outcome();
    };

    socket.addEventListener("open", () => socket.send(JSON.stringify(send)));

    socket.addEventListener("message", (event) => {
      received.push(JSON.parse(String(event.data)) as ServerMessage);
      if (received.length >= wanted) {
        finish(() => resolve(received));
      }
    });

    socket.addEventListener("error", () => {
      finish(() =>
        reject(new Error(`Cannot reach ${STUDIO_URL}. Start the server with './gradlew :server:run' first.`)),
      );
    });
  });
}

/** A molecule the server has stored, as its HTTP API describes it. */
interface Stored {
  id: string;
}

/** Stores a molecule over the HTTP API. */
async function store(smiles: string): Promise<Stored> {
  const response = await fetch(`${API_URL}/api/molecules`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ smiles }),
  });

  if (!response.ok) {
    throw new Error(`Cannot store ${smiles}: ${response.status}. Start the server with './gradlew :server:run' first.`);
  }

  return (await response.json()) as Stored;
}

describe("the Studio socket", () => {
  const sockets: StudioSocket[] = [];

  /** Opens a connected socket, closed again when the test ends. */
  async function connected(): Promise<StudioSocket> {
    const socket = new StudioSocket({ url: STUDIO_URL, keepAliveMs: 0, requestTimeoutMs: TIMEOUT_MS });
    sockets.push(socket);
    await socket.connect();
    return socket;
  }

  afterEach(() => {
    sockets.splice(0).forEach((socket) => socket.close());
  });

  it("greets a new session and answers a ping with a pong", async () => {
    const [ready, pong] = await exchange({ type: "ping", sequence: 108 }, 2);

    expect(ready?.type).toBe("session.ready");
    expect(pong).toEqual(expect.objectContaining({ type: "pong", sequence: 108 }));
  });

  it("greets a client that connects through the socket", async () => {
    const socket = new StudioSocket({ url: STUDIO_URL, keepAliveMs: 0 });
    sockets.push(socket);

    const ready = await socket.connect();

    expect(ready.type).toBe("session.ready");
    expect(ready.protocol_version).toBe(PROTOCOL_VERSION);
    expect(ready.connection_id).toMatch(/^ws-/);
    expect(socket.isOpen).toBe(true);
  });

  it("opens a session on a stored molecule", async () => {
    const stored = await store("CCO");
    const socket = await connected();

    const state = await socket.openSession(stored.id);

    expect(state.type).toBe("session.state");
    expect(state.version).toBe(1);
    expect(state.source_molecule_id).toBe(stored.id);
    expect(state.saved_molecule_id).toBeNull();
    expect(state.smiles).toBe("CCO");

    // ethanol's three written atoms, and the six hydrogens it counted
    expect(state.atoms).toHaveLength(9);
    expect(state.bonds).toHaveLength(8);
    expect(state.atoms.filter((atom) => atom.was_implicit)).toHaveLength(6);
    expect(state.fragments).toHaveLength(1);

    const first = state.atoms[0];
    expect(first?.element).toBe("C");
    expect(first?.colour).toMatch(/^#/);
    expect(Number.isFinite(first?.position.x)).toBe(true);
  });

  it("sends the state of a session again when asked", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);

    const again = await socket.resync(opened.session_id, opened.version);

    expect(again.session_id).toBe(opened.session_id);
    expect(again.version).toBe(opened.version);
  });

  it("tells a subscriber about the state it did not ask for", async () => {
    const stored = await store("CCO");
    const socket = await connected();

    const seen: SessionState[] = [];
    socket.on<SessionState>("session.state", (state) => seen.push(state));
    await socket.openSession(stored.id);

    expect(seen).toHaveLength(1);
    expect(seen[0]?.source_molecule_id).toBe(stored.id);
  });

  it("rejects a request the server refuses", async () => {
    const socket = await connected();

    await expect(socket.openSession("not-a-molecule")).rejects.toThrow(StudioError);
    await expect(socket.openSession("not-a-molecule")).rejects.toMatchObject({
      code: "UNKNOWN_MOLECULE",
      answering: "session.open",
    });

    // the refusal did not cost us the connection
    expect(socket.isOpen).toBe(true);
  });

  it("rebuilds a session on a submitted SMILES", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);

    const reply = await socket.submitSmiles(opened.session_id, opened.version, "CCN");

    expect(reply.type).toBe("session.state");
    const state = reply as SessionState;
    expect(state.session_id).toBe(opened.session_id);
    expect(state.version).toBe(opened.version + 1);
    expect(state.smiles).toBe("CCN");
    expect(state.atoms).toHaveLength(10);
  });

  it("answers a submit made on a stale version with a conflict carrying the current state", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);
    await socket.submitSmiles(opened.session_id, opened.version, "CCN");

    const reply = await socket.submitSmiles(opened.session_id, opened.version, "CCC");

    expect(reply.type).toBe("conflict");
    if (reply.type === "conflict") {
      expect(reply.expected_version).toBe(opened.version + 1);
      expect(reply.state.smiles).toBe("CCN");
    }
  });

  it("rejects a submitted SMILES it cannot read, leaving the session as it was", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);

    await expect(socket.submitSmiles(opened.session_id, opened.version, "C(")).rejects.toMatchObject({
      code: "INVALID_SMILES",
      answering: "smiles.submit",
    });

    const again = await socket.resync(opened.session_id, opened.version);
    expect(again.version).toBe(opened.version);
    expect(again.smiles).toBe("CCO");
  });

  it("moves an atom, acknowledges it, and undoes and redoes it", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);
    const first = opened.atoms[0]!;
    const target = { x: 4, y: 5, z: 6 };

    const ack = await socket.patchAtoms(opened.session_id, opened.version, [{ id: first.id, position: target }]);
    expect(ack.type).toBe("atom.patch.ack");
    if (ack.type !== "atom.patch.ack") {
      return;
    }
    expect(ack.version).toBe(opened.version + 1);
    expect(ack.can_undo).toBe(true);

    const undone = await socket.step("undo", opened.session_id, ack.version);
    expect(undone.type).toBe("edit.applied");
    if (undone.type !== "edit.applied") {
      return;
    }
    expect(undone.atoms).toEqual([{ id: first.id, position: first.position }]);
    expect(undone.can_redo).toBe(true);

    const redone = await socket.step("redo", opened.session_id, undone.version);
    expect(redone.type === "edit.applied" && redone.atoms[0]!.position).toEqual(target);
  });

  it("gives a bond a length and a torsion, answering with the atoms that moved, and refuses what it cannot", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);
    const bond = opened.bonds[0]!;
    const distance = (state: SessionState, a: string, b: string) => {
      const [p, q] = [a, b].map((id) => state.atoms.find((atom) => atom.id === id)!.position);
      return Math.hypot(p!.x - q!.x, p!.y - q!.y, p!.z - q!.z);
    };

    const longer = await socket.setBondLength(opened.session_id, opened.version, bond.id, 2, bond.first);
    expect(longer.type).toBe("bond.length.ack");
    if (longer.type !== "bond.length.ack") {
      return;
    }
    expect(longer.version).toBe(opened.version + 1);
    expect(longer.atoms.map((atom) => atom.id)).toContain(bond.second);
    expect(longer.atoms.map((atom) => atom.id)).not.toContain(bond.first);

    // a bond to a hydrogen on the first carbon and the oxygen on the second, turned about the bond between them
    const hydrogen = opened.atoms.find((atom) => atom.element === "H")!;
    const oxygen = opened.atoms[2]!;
    const turned = await socket.setBondTorsion(opened.session_id, longer.version, bond.id, hydrogen.id, oxygen.id, 60, bond.first);
    expect(turned.type).toBe("bond.torsion.ack");
    expect(turned.type === "bond.torsion.ack" && turned.atoms.length).toBeGreaterThan(0);

    await expect(socket.setBondTorsion(opened.session_id, longer.version + 1, bond.id, oxygen.id, hydrogen.id, 60, null)).rejects.toMatchObject({ code: "INVALID_EDIT" });
    await expect(socket.setBondLength(opened.session_id, longer.version + 1, bond.id, 99, null)).rejects.toMatchObject({ code: "INVALID_EDIT" });

    const stale = await socket.setBondLength(opened.session_id, opened.version, bond.id, 1.5, null);
    expect(stale.type).toBe("conflict");
    if (stale.type === "conflict") {
      expect(distance(stale.state, bond.first, bond.second)).toBeCloseTo(2, 6);
    }
  });

  it("drags a bond as several uncommitted changes and one commit, which is one edit to undo", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);
    const bond = opened.bonds[0]!;

    const first = await socket.setBondLength(opened.session_id, opened.version, bond.id, 1.7, bond.first, false);
    expect(first.type).toBe("bond.length.ack");
    if (first.type !== "bond.length.ack") {
      return;
    }
    const second = await socket.setBondLength(opened.session_id, first.version, bond.id, 1.9, bond.first, false);
    expect(second.type === "bond.length.ack" && second.saved_molecule_id).toBeNull();
    if (second.type !== "bond.length.ack") {
      return;
    }

    const dropped = await socket.setBondLength(opened.session_id, second.version, bond.id, 1.9, bond.first, true);
    expect(dropped.type === "bond.length.ack" && dropped.atoms).toEqual([]);
    if (dropped.type !== "bond.length.ack") {
      return;
    }
    expect(dropped.can_undo).toBe(true);
    expect(dropped.saved_molecule_id).not.toBeNull();

    const undone = await socket.step("undo", opened.session_id, dropped.version);
    expect(undone.type === "edit.applied" && undone.can_undo).toBe(false);
  });

  it("answers a patch made on a stale version with a conflict, and refuses an unknown atom", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);
    const where = { x: 1, y: 1, z: 1 };

    const stale = await socket.patchAtoms(opened.session_id, opened.version + 5, [{ id: opened.atoms[0]!.id, position: where }]);
    expect(stale.type).toBe("conflict");

    await expect(socket.patchAtoms(opened.session_id, opened.version, [{ id: "a999", position: where }])).rejects.toMatchObject({
      code: "UNKNOWN_ID",
      answering: "atom.patch",
    });
  });

  it("locks an atom so that a patch of it is refused, and unlocks it again", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);
    const first = opened.atoms[0]!;
    const where = { x: 2, y: 2, z: 2 };

    const locked = await socket.lockAtoms(opened.session_id, opened.version, [first.id], true);
    expect(locked.type === "atom.lock.ack" && locked.locked).toEqual([first.id]);
    if (locked.type !== "atom.lock.ack") {
      return;
    }

    await expect(socket.patchAtoms(opened.session_id, locked.version, [{ id: first.id, position: where }])).rejects.toMatchObject({
      code: "LOCKED",
    });

    const unlocked = await socket.lockAtoms(opened.session_id, locked.version, [first.id], false);
    expect(unlocked.type === "atom.lock.ack" && unlocked.locked).toEqual([]);
  });

  it("moves the molecule as a whole, undoes it, and leaves every atom where it was", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);
    expect(opened.transform).toEqual(IDENTITY_POSE);

    // a quarter turn about z, and a shift
    const half = Math.SQRT1_2;
    const pose = { position: { x: 3, y: -1, z: 2 }, rotation: { x: 0, y: 0, z: half, w: half } };

    const dragging = await socket.transformMolecule(opened.session_id, opened.version, pose, false);
    expect(dragging.type === "molecule.transform.ack" && dragging.can_undo).toBe(true);
    if (dragging.type !== "molecule.transform.ack") {
      return;
    }
    const dropped = await socket.transformMolecule(opened.session_id, dragging.version, pose, true);
    expect(dropped.type === "molecule.transform.ack" && dropped.saved_molecule_id).toBeTruthy();
    if (dropped.type !== "molecule.transform.ack") {
      return;
    }

    const synced = await socket.resync(opened.session_id, dropped.version);
    expect(synced.transform.position).toEqual(pose.position);
    expect(synced.transform.rotation.z).toBeCloseTo(half, 12);
    expect(synced.atoms.map((atom) => atom.position)).toEqual(opened.atoms.map((atom) => atom.position));

    const undone = await socket.step("undo", opened.session_id, synced.version);
    expect(undone.type === "edit.applied" && undone.transform).toEqual(IDENTITY_POSE);
    expect(undone.type === "edit.applied" && undone.atoms).toEqual([]);
  });

  it("tells the strain of the geometry on its own once the session is left alone, and more of it after an edit", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const results: GeometryResult[] = [];
    socket.on<GeometryResult>("geometry.result", (result) => results.push(result));

    const opened = await socket.openSession(stored.id);
    await vi.waitFor(() => expect(results).toHaveLength(1), { timeout: TIMEOUT_MS });
    expect(results[0]).toMatchObject({ session_id: opened.session_id, version: opened.version, status: "complete", strain: 0, findings: [] });

    // an atom put on top of another
    const [first, , third] = opened.atoms as [SessionAtom, SessionAtom, SessionAtom];
    const ack = await socket.patchAtoms(opened.session_id, opened.version, [{ id: first.id, position: third.position }]);
    expect(ack.type).toBe("atom.patch.ack");

    await vi.waitFor(() => expect(results).toHaveLength(2), { timeout: TIMEOUT_MS });
    expect(results[1]!.version).toBe(opened.version + 1);
    expect(results[1]!.strain).toBeGreaterThan(10);
    expect(results[1]!.findings.length).toBeGreaterThan(0);
    expect(results[1]!.clashes.length).toBeGreaterThan(0);
  });

  it("relaxes a distorted geometry as one edit, reporting progress, and takes it back in one undo", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const progress: OptimiseProgress[] = [];
    const results: OptimiseResult[] = [];
    socket.on<OptimiseProgress>("optimise.progress", (message) => progress.push(message));
    socket.on<OptimiseResult>("optimise.result", (message) => results.push(message));

    const opened = await socket.openSession(stored.id);
    const first = opened.atoms[0]!;
    const distorted = await socket.patchAtoms(opened.session_id, opened.version, [
      { id: first.id, position: { ...first.position, x: first.position.x + 0.6 } },
    ]);
    if (distorted.type !== "atom.patch.ack") {
      throw new Error(`Expected the patch to be acknowledged, not ${distorted.type}`);
    }

    const started = await socket.optimise(opened.session_id, distorted.version);
    expect(started.type).toBe("optimise.started");

    await vi.waitFor(() => expect(results).toHaveLength(1), { timeout: TIMEOUT_MS });
    const [result] = results as [OptimiseResult];
    expect(result).toMatchObject({ session_id: opened.session_id, base_version: distorted.version, status: "complete", can_undo: true });
    expect(result.version).toBe(distorted.version + 1);
    expect(result.atoms.length).toBeGreaterThan(0);
    expect(result.strain_after!).toBeLessThan(result.strain_before!);
    expect(progress[0]).toMatchObject({ session_id: opened.session_id, iteration: 0 });

    const undone = await socket.step("undo", opened.session_id, result.version);
    expect(undone.type === "edit.applied" && undone.atoms.map((atom) => atom.id).sort()).toEqual(result.atoms.map((atom) => atom.id).sort());
  });

  it("answers an optimise made on a stale version with a conflict, and a cancel with nothing to stop as not cancelling", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);

    const stale = await socket.optimise(opened.session_id, opened.version + 5);
    expect(stale.type).toBe("conflict");

    const cancel = await socket.cancelOptimise(opened.session_id);
    expect(cancel.cancelling).toBe(false);
  });

  it("refuses a rotation of no length", async () => {
    const stored = await store("CCO");
    const socket = await connected();
    const opened = await socket.openSession(stored.id);

    await expect(
      socket.transformMolecule(opened.session_id, opened.version, { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 0 } }),
    ).rejects.toMatchObject({ code: "INVALID_MESSAGE" });
  });

  it("refuses to send on a connection that is not open", () => {
    const socket = new StudioSocket({ url: STUDIO_URL });

    expect(() => socket.send("ping")).toThrow(StudioError);
  });
});

/** A server that greets with whatever protocol it is told to, and records what it is sent, with no network involved. */
function stubServer(protocol: number): { sent: string[]; restore: () => void } {
  const real = globalThis.WebSocket;
  const sent: string[] = [];

  class Stub extends EventTarget {
    static readonly OPEN = 1;
    readyState = 1;
    closed = false;

    constructor() {
      super();
      queueMicrotask(() => {
        const data = JSON.stringify({ type: "session.ready", connection_id: "ws-stub", protocol_version: protocol });
        this.dispatchEvent(Object.assign(new Event("message"), { data }));
      });
    }

    send(text: string): void {
      sent.push(text);
    }

    close(): void {
      this.closed = true;
      this.readyState = 3;
    }
  }

  globalThis.WebSocket = Stub as unknown as typeof WebSocket;
  return {
    sent,
    restore: () => {
      globalThis.WebSocket = real;
    },
  };
}

describe("StudioSocket protocol check", () => {
  it("names its protocol in every message it sends", async () => {
    const stub = stubServer(PROTOCOL_VERSION);
    try {
      const socket = new StudioSocket({ url: "ws://stub", keepAliveMs: 0 });
      await socket.connect();
      socket.send("ping");
      socket.send("session.resync", { session_id: "s" });

      expect(stub.sent.map((text) => JSON.parse(text).protocol_version)).toEqual([PROTOCOL_VERSION, PROTOCOL_VERSION]);
      socket.close();
    } finally {
      stub.restore();
    }
  });

  it("refuses a server that speaks another protocol, and says which side is out of date", async () => {
    for (const [protocol, advice] of [
      [PROTOCOL_VERSION - 1, "restart"],
      [PROTOCOL_VERSION + 1, "reload"],
    ] as const) {
      const stub = stubServer(protocol);
      try {
        const told = vi.fn();
        const socket = new StudioSocket({ url: "ws://stub", keepAliveMs: 0, onIncompatible: told });

        await expect(socket.connect()).rejects.toMatchObject({ code: "PROTOCOL_MISMATCH", message: expect.stringContaining(advice) });
        expect(told).toHaveBeenCalledOnce();
        expect(stub.sent).toEqual([]);
      } finally {
        stub.restore();
      }
    }
  });
});
