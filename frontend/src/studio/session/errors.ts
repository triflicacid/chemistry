import {ApiError} from "../../api/http";
import {StudioError} from "./studioSocket";

/** Whether the failure was the molecule itself being unusable, rather than the server or network. */
export function isUnknownMolecule(cause: unknown): boolean {
  if (cause instanceof ApiError) {
    return cause.status === 400;
  }
  return cause instanceof StudioError && (cause.code === "UNKNOWN_MOLECULE" || cause.code === "INVALID_MESSAGE");
}

/** What to tell the user about a failure. */
export function reason(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
