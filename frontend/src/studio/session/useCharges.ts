import {useCallback, useEffect, useRef, useState} from "react";
import type {ChargesProgress, ChargesResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";
import {useWarner} from "./warnings";

/** A calculation of charges that has been asked for and has not yet answered. */
export interface Calculating {
  /** The model asked for. */
  model: string;
  /** When it was asked for, as {@link Date.now} says. */
  startedAt: number;
}

export interface Charges {
  /** The partial charges as the server last said, or null if it has not. They describe the version in `version`. */
  charges: ChargesResult | null;
  /** The calculation under way, or null if there is none. */
  calculating: Calculating | null;
  /** How the calculation under way is getting on, or null if the server has not said yet. */
  progress: ChargesProgress | null;
  /**
   * Asks the server for the charges of the session as it is now. Several asks close together are answered once, and one
   * that comes while another is under way abandons it.
   *
   * @param model the name of the model to assign them with
   * @param multiplicity the spin multiplicity to calculate the whole session at, or null for the lowest the electron count allows
   * @param automatic whether it is asked only because the session changed, so that an answer that says what the last did is not warned of again
   */
  requestCharges: (model: string, multiplicity: number | null, automatic?: boolean) => void;
}

/**
 * Keeps the partial charges the server sends, how it is getting on with them, and asks for them.
 *
 * @param link the connection
 */
export function useCharges({ socketRef, held, note, warn }: SessionLink): Charges {
  const warnOnce = useWarner(warn);
  const [charges, setCharges] = useState<ChargesResult | null>(null);
  const [calculating, setCalculating] = useState<Calculating | null>(null);
  const [progress, setProgress] = useState<ChargesProgress | null>(null);
  // what the latest request asked for, which the listeners read without being made again by it
  const asked = useRef<string | null>(null);
  const automatically = useRef(false);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    const hearProgress = socket.on<ChargesProgress>("charges.progress", (report) => {
      if ((held.current && held.current.session_id !== report.session_id) || report.requested !== asked.current) {
        return;
      }
      setProgress(report);
    });

    const hearResult = socket.on<ChargesResult>("charges.result", (result) => {
      // an answer to a model that has since been swapped for another is of no use
      if ((held.current && held.current.session_id !== result.session_id) || result.requested !== asked.current) {
        return;
      }
      setCalculating(null);
      setProgress(null);
      warnOnce(result.status === "complete" ? null : (result.message ?? "The charges could not be calculated."), automatically.current);
      // a result older than the one held is stale
      setCharges((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });

    return () => {
      hearProgress();
      hearResult();
    };
  }, [held, socketRef, warnOnce]);

  const requestCharges = useCallback(
    (model: string, multiplicity: number | null, automatic = false) => {
      automatically.current = automatic;
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      asked.current = model;
      setCalculating({ model, startedAt: Date.now() });
      setProgress(null);
      socket.requestCharges(base.session_id, model, multiplicity).catch((cause: unknown) => {
        setCalculating(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  return { charges, calculating, progress, requestCharges };
}
