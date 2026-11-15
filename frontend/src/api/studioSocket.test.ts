import { describe, expect, it } from "vitest";

/**
 * The Studio editing channel. Points at the backend directly rather than through the Vite
 * proxy, so a failure here means the server, not the dev server's configuration.
 */
const STUDIO_URL = process.env.WS_URL ?? "ws://localhost:8081/ws/studio";

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

describe("the Studio socket", () => {
  it("greets a new session and answers a ping with a pong", async () => {
    const [ready, pong] = await exchange({ type: "ping", sequence: 108 }, 2);

    expect(ready?.type).toBe("session.ready");
    expect(pong).toEqual(expect.objectContaining({ type: "pong", sequence: 108 }));
  });
});
