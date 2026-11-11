/** Calls the server's health endpoint and returns the HTTP status and OK flag. */
export async function health(): Promise<{ status: number; ok: boolean }> {
  const response = await fetch("/api/health");
  return { status: response.status, ok: response.ok };
}
