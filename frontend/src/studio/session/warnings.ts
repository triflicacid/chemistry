import {useCallback, useRef} from "react";

/**
 * Says that something cannot be done, once.
 *
 * @param warn says it
 * @returns a function to tell each result to: the reason it was not done, or null if it was; it warns only when the reason is new
 */
export function useWarnOnce(warn: (message: string) => void): (reason: string | null) => void {
  const last = useRef<string | null>(null);
  return useCallback(
    (reason: string | null) => {
      if (reason !== null && reason !== last.current) {
        warn(reason);
      }
      last.current = reason;
    },
    [warn],
  );
}
