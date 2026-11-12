/** A failed API call: either the server's JSON error, or a network failure (status 0). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** How long to wait for a response before giving up. */
const REQUEST_TIMEOUT_MS = 15_000;

interface RequestOptions {
  /** Sent as the JSON body. */
  json?: unknown;
  headers?: Record<string, string>;
  /** What the caller wants back; sent as the Accept header. */
  accept?: string;
}

/** Sends requests to the server and turns failures into {@link ApiError}s. */
export class HttpClient {
  constructor(private readonly baseUrl = "") {}

  /** Sends a request and returns the successful response. */
  async send(method: string, path: string, options: RequestOptions = {}): Promise<Response> {
    const headers: Record<string, string> = { ...options.headers };
    if (options.accept) {
      headers["Accept"] = options.accept;
    }
    if (options.json !== undefined) {
      headers["Content-Type"] = "application/json";
    }

    let response: Response;
    try {
      response = await fetch(this.baseUrl + path, {
        method,
        headers,
        body: options.json === undefined ? undefined : JSON.stringify(options.json),
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === "TimeoutError") {
        throw new ApiError(0, "TIMEOUT", `The server did not respond within ${REQUEST_TIMEOUT_MS / 1000} seconds`);
      }
      throw new ApiError(0, "NETWORK", `Cannot reach the server (${String(cause)})`);
    }

    if (!response.ok) {
      throw await HttpClient.failure(response);
    }
    return response;
  }

  async json<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
    const response = await this.send(method, path, { ...options, accept: "application/json" });
    return (await response.json()) as T;
  }

  async text(method: string, path: string, options: RequestOptions = {}): Promise<string> {
    const response = await this.send(method, path, options);
    return response.text();
  }

  /** Reads the server's `{"error": {"code", "message"}}` body, falling back to the status line. */
  private static async failure(response: Response): Promise<ApiError> {
    try {
      const body = (await response.json()) as { error?: { code?: string; message?: string } };
      if (body.error?.message) {
        return new ApiError(response.status, body.error.code ?? "UNKNOWN", body.error.message);
      }
    } catch {
      // not JSON; use the status line below
    }
    return new ApiError(response.status, "HTTP", `${response.status} ${response.statusText}`.trim());
  }
}
