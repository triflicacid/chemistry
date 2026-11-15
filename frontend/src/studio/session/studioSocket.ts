import type { ServerEnvelope, SessionReady, SessionState } from "./messages";

/** The error the server answered a message with. */
export class StudioError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly answering?: string,
  ) {
    super(message);
    this.name = "StudioError";
  }
}

export interface StudioSocketOptions {
  url?: string;
  /** How long the connection may stay quiet before it is pinged. Zero turns pinging off. */
  keepAliveMs?: number;
  /** How long to wait for a reply before giving up on it. */
  requestTimeoutMs?: number;
  /** How long to wait before the first attempt to reconnect. */
  minRetryMs?: number;
  /** The longest the backoff grows to. */
  maxRetryMs?: number;
  /** Told whenever the server greets the connection, including after a reconnect. */
  onReady?: (ready: SessionReady) => void;
  /** Told when the connection drops, before a reconnect is scheduled. */
  onDrop?: (reason: string) => void;
}

/** A message waiting for its reply. */
interface Pending {
  replyType: string;
  resolve: (message: ServerEnvelope) => void;
  reject: (cause: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

const DEFAULTS = {
  keepAliveMs: 20_000,
  requestTimeoutMs: 30_000,
  minRetryMs: 500,
  maxRetryMs: 10_000,
};

/** Every message, whatever its type, reaches a listener subscribed to this. */
export const ANY = "*";

/** The Studio editing channel: sends typed messages, matches replies, and reconnects if dropped. */
export class StudioSocket {
  private readonly url: string;
  private readonly options: Required<Omit<StudioSocketOptions, "url" | "onReady" | "onDrop">> &
    Pick<StudioSocketOptions, "onReady" | "onDrop">;

  private socket: WebSocket | null = null;
  private sequence = 0;
  private attempt = 0;
  private wanted = false;
  private readonly listeners = new Map<string, Set<(message: ServerEnvelope) => void>>();
  private readonly pending = new Map<number, Pending>();
  private quiet?: ReturnType<typeof setTimeout>;
  private retry?: ReturnType<typeof setTimeout>;

  constructor(options: StudioSocketOptions = {}) {
    this.url = options.url ?? defaultUrl();
    this.options = { ...DEFAULTS, ...options };
  }

  /** Whether the connection can be written to right now. */
  get isOpen(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  /** Opens the connection and resolves with the server's greeting. */
  connect(): Promise<SessionReady> {
    this.wanted = true;
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(this.url);
      this.socket = socket;

      const greeted = this.on<SessionReady>("session.ready", (ready) => {
        greeted();
        resolve(ready);
      });

      socket.addEventListener("message", (event) => this.receive(String(event.data)));

      socket.addEventListener("close", (event) => {
        greeted();
        this.dropped(`${event.code}: ${event.reason || "the connection closed"}`);
        reject(new StudioError("CLOSED", `Cannot reach ${this.url}`));
      });

      socket.addEventListener("error", () => {
        greeted();
        reject(new StudioError("NETWORK", `Cannot reach ${this.url}`));
      });
    });
  }

  /** Closes the connection and stops it reconnecting. */
  close(): void {
    this.wanted = false;
    clearTimeout(this.quiet);
    clearTimeout(this.retry);
    this.fail(new StudioError("CLOSED", "The connection was closed"));
    this.socket?.close();
    this.socket = null;
  }

  /**
   * Subscribes to a type of message, or to {@link ANY} for all of them.
   *
   * @returns a function that unsubscribes
   */
  on<T extends ServerEnvelope>(type: string, listener: (message: T) => void): () => void {
    const held = listener as (message: ServerEnvelope) => void;
    const forType = this.listeners.get(type) ?? new Set();
    forType.add(held);
    this.listeners.set(type, forType);
    return () => forType.delete(held);
  }

  /**
   * @returns the sequence number it was sent with
   */
  send(type: string, payload: Record<string, unknown> = {}): number {
    if (!this.socket || !this.isOpen) {
      throw new StudioError("CLOSED", "The connection is not open");
    }

    const sequence = ++this.sequence;
    this.socket.send(JSON.stringify({ ...payload, type, sequence }));
    this.rearm();
    return sequence;
  }

  /** Sends a message and resolves with the reply of `replyType`, or rejects with the server's error. */
  request<T extends ServerEnvelope>(type: string, payload: Record<string, unknown>, replyType: string): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      let sequence: number;
      try {
        sequence = this.send(type, payload);
      } catch (cause) {
        reject(cause as Error);
        return;
      }

      const timer = setTimeout(() => {
        this.pending.delete(sequence);
        reject(new StudioError("TIMEOUT", `The server did not answer '${type}' in time`, type));
      }, this.options.requestTimeoutMs);

      this.pending.set(sequence, {
        replyType,
        resolve: resolve as (message: ServerEnvelope) => void,
        reject,
        timer,
      });
    });
  }

  /** Opens a session on a stored molecule. */
  openSession(moleculeId: string, conformers?: number): Promise<SessionState> {
    const payload: Record<string, unknown> = { molecule_id: moleculeId };
    if (conformers !== undefined) {
      payload.conformers = conformers;
    }
    return this.request<SessionState>("session.open", payload, "session.state");
  }

  /** Asks for the authoritative state of a session, after a reconnect or a gap in the sequence. */
  resync(sessionId: string, sinceVersion: number): Promise<SessionState> {
    return this.request<SessionState>(
      "session.resync",
      { session_id: sessionId, since_version: sinceVersion },
      "session.state",
    );
  }

  private receive(text: string): void {
    let message: ServerEnvelope;
    try {
      message = JSON.parse(text) as ServerEnvelope;
    } catch {
      // a server that sends something other than JSON is broken; there is nothing to do here
      return;
    }

    this.rearm();

    if (message.type === "session.ready") {
      this.attempt = 0;
      this.options.onReady?.(message as SessionReady);
    }

    this.answer(message);
    this.notify(message);
  }

  /** Settles the request a message is the reply to, if it is one. */
  private answer(message: ServerEnvelope): void {
    if (typeof message.sequence !== "number") {
      return;
    }

    const waiting = this.pending.get(message.sequence);
    if (!waiting || (message.type !== waiting.replyType && message.type !== "error")) {
      return;
    }

    this.pending.delete(message.sequence);
    clearTimeout(waiting.timer);

    if (message.type === "error") {
      const failure = message as ServerEnvelope & { answering?: string; error?: { code?: string; message?: string } };
      waiting.reject(
        new StudioError(failure.error?.code ?? "UNKNOWN", failure.error?.message ?? "The server refused the message", failure.answering),
      );
      return;
    }

    waiting.resolve(message);
  }

  private notify(message: ServerEnvelope): void {
    this.listeners.get(message.type)?.forEach((listener) => listener(message));
    this.listeners.get(ANY)?.forEach((listener) => listener(message));
  }

  /** Gives up on every reply still outstanding. */
  private fail(cause: Error): void {
    this.pending.forEach((waiting) => {
      clearTimeout(waiting.timer);
      waiting.reject(cause);
    });
    this.pending.clear();
  }

  private dropped(reason: string): void {
    this.socket = null;
    clearTimeout(this.quiet);
    this.fail(new StudioError("DROPPED", reason));

    if (!this.wanted) {
      return;
    }

    this.options.onDrop?.(reason);

    // the backoff grows with each failed attempt, so a server that is down is not hammered
    const wait = Math.min(this.options.minRetryMs * 2 ** this.attempt, this.options.maxRetryMs);
    this.attempt += 1;
    this.retry = setTimeout(() => {
      if (this.wanted) {
        this.connect().catch(() => undefined);
      }
    }, wait);
  }

  /** Pings once the connection has been quiet for long enough to be worth testing. */
  private rearm(): void {
    clearTimeout(this.quiet);
    if (this.options.keepAliveMs <= 0) {
      return;
    }

    this.quiet = setTimeout(() => {
      if (this.isOpen) {
        this.send("ping");
      }
    }, this.options.keepAliveMs);
  }
}

/** Where the page's own host serves the channel, which the dev server proxies to the backend. */
function defaultUrl(): string {
  if (typeof location === "undefined") {
    throw new StudioError("NO_URL", "There is no page to take a URL from, so one must be given");
  }

  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${location.host}/ws/studio`;
}
