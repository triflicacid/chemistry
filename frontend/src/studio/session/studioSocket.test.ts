import { afterEach, describe, expect, it } from "vitest";
import { StudioError, StudioSocket } from "./studioSocket";
import type { SessionState } from "./messages";

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
    expect(ready.protocol_version).toBe(2);
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

  it("refuses to send on a connection that is not open", () => {
    const socket = new StudioSocket({ url: STUDIO_URL });

    expect(() => socket.send("ping")).toThrow(StudioError);
  });
});
