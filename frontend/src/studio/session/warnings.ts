import {useCallback, useEffect, useRef} from "react";

/**
 * Says that something cannot be done. A result warns every time, since it answers something the user did, except one that answers a
 * request made on the user's behalf because the session changed, and says the same as the one before: what is asked for again with every
 * change to the structure, such as the forces of one molecule, would otherwise warn on every edit.
 *
 * @param warn says it
 * @returns a function to tell each result to: the reason it was not done, or null if it was; and whether it answers an automatic request
 */
export function useWarner(warn: (message: string) => void): (reason: string | null, automatic?: boolean) => void {
  const last = useRef<string | null>(null);
  return useCallback(
    (reason: string | null, automatic = false) => {
      if (reason !== null && !(automatic && reason === last.current)) {
        warn(reason);
      }
      last.current = reason;
    },
    [warn],
  );
}

/**
 * Asks for something whenever it is wanted and the session changes under it, and says which asks are automatic. An ask is automatic if
 * it is made only because the session changed, and not because it was just wanted, or what it is of was changed.
 *
 * @param wanted whether it is wanted
 * @param settings what it is of, as a string, which differs whenever what it is of does
 * @param version the version of the session, which changes with every edit to it
 * @param ask asks for it, told whether the ask is automatic
 * @param again what else makes it be asked for again, as a string, which does so automatically, as when a slice plane is slid along
 */
export function useAsk(wanted: boolean, settings: string, version: number | undefined, ask: (automatic: boolean) => void, again = ""): void {
  const before = useRef<string | null>(null);
  const latest = useRef(ask);
  latest.current = ask;

  useEffect(() => {
    if (!wanted) {
      before.current = null;
      return;
    }
    const automatic = before.current === settings;
    before.current = settings;
    latest.current(automatic);
  }, [wanted, settings, version, again]);
}
