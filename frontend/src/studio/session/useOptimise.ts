import {type RefObject, useCallback, useEffect, useMemo, useState} from "react";
import type {OptimiseProgress, OptimiseResult} from "./messages";
import {applyAck} from "./acks";
import {reason} from "./errors";
import type {EditResult, OptimisationOutcome, Optimising} from "./types";
import type {Edits} from "./useEdits";
import type {SessionLink, SocketEvents} from "./useSocket";

export interface Optimisation {
  /** The optimisation under way, or null if there is none. */
  optimising: Optimising | null;
  /** How the latest optimisation ended. */
  outcome: OptimisationOutcome | null;
  optimise: () => Promise<EditResult>;
  cancelOptimise: () => Promise<void>;
}

/**
 * Runs optimisations on the server and keeps how they are going and how the last ended.
 *
 * @param link the connection
 * @param serially runs the optimisation's request in turn with the edits
 * @param events where the connection is told what to do when the session changes or the connection drops
 */
export function useOptimise({ socketRef, held, adopt, note }: SessionLink, { serially }: Pick<Edits, "serially">, events: RefObject<SocketEvents>): Optimisation {
  const [optimising, setOptimising] = useState<Optimising | null>(null);
  const [outcome, setOutcome] = useState<OptimisationOutcome | null>(null);

  events.current = useMemo(
    () => ({
      reset: () => {
        setOptimising(null);
        setOutcome(null);
      },
      dropped: () => setOptimising(null),
    }),
    [],
  );

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    const hearProgress = socket.on<OptimiseProgress>("optimise.progress", (progress) => {
      if (held.current && held.current.session_id !== progress.session_id) {
        return;
      }
      // a report that arrives after the result, or on a connection that never asked, has nothing to update
      setOptimising((before) => (before ? { iteration: progress.iteration, history: [...before.history, { iteration: progress.iteration, energy: progress.energy }] } : before));
    });

    const hearResult = socket.on<OptimiseResult>("optimise.result", (result) => {
      const base = held.current;
      if (!base || base.session_id !== result.session_id) {
        return;
      }

      setOptimising(null);
      setOutcome({
        at: Date.now(),
        status: result.status,
        strainBefore: result.strain_before,
        strainAfter: result.strain_after,
        atoms: result.atoms.length,
        iterations: result.iterations,
        history: result.energy_history,
        message: result.message,
        warnings: result.warnings,
      });
      for (const warning of result.warnings) {
        note(`Optimiser: ${warning}`, true);
      }

      switch (result.status) {
        case "complete": {
          const [before, after] = [result.strain_before, result.strain_after];
          note(
            `Optimised ${result.atoms.length} atoms in ${result.iterations} steps` +
              (before !== null && after !== null ? `: strain ${before.toFixed(1)} → ${after.toFixed(1)}` : ""),
          );

          if (base.version === result.base_version) {
            adopt(applyAck(base, result.atoms, result));
          } else if (base.version !== result.version) {
            // the sparse result says nothing of an atom the client has moved since, so ask for the whole state
            socket.resync(base.session_id, base.version).then(adopt).catch((cause: unknown) => note(reason(cause), true));
          }
          break;
        }
        case "unchanged":
          note("The geometry is already as relaxed as it can be");
          break;
        case "failed":
          note(result.message ?? "The optimisation failed", true);
          break;
        default:
          note(result.message ?? "The optimisation was cancelled");
      }
    });

    return () => {
      hearProgress();
      hearResult();
    };
  }, [adopt, held, note, socketRef]);

  const optimise = useCallback(
    (): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to optimise." };
        }

        // set before asking, because a small molecule can finish before the server's answer to the request arrives
        setOptimising({ iteration: 0, history: [] });
        setOutcome(null);
        try {
          const reply = await socket.optimise(base.session_id, base.version);
          if (reply.type === "conflict") {
            setOptimising(null);
            adopt(reply.state);
            note("The structure changed, so it was not optimised", true);
            return { kind: "conflict" };
          }

          note("Optimising");
          return { kind: "applied" };
        } catch (cause) {
          setOptimising(null);
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, held, note, serially, socketRef],
  );

  const cancelOptimise = useCallback(async (): Promise<void> => {
    const socket = socketRef.current;
    const base = held.current;
    if (!socket || !base) {
      return;
    }

    try {
      await socket.cancelOptimise(base.session_id);
    } catch (cause) {
      note(reason(cause), true);
    }
  }, [held, note, socketRef]);

  return { optimising, outcome, optimise, cancelOptimise };
}
