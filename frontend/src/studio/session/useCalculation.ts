import {useCallback, useEffect, useState} from "react";
import type {CalculationResult, ElectronsAck} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";

export interface CalculationInfo {
  lastCalculation: CalculationResult | null;
  requestElectrons: () => Promise<ElectronsAck | null>;
  resetCalculations: () => Promise<boolean>;
}

/**
 * Keeps what the server says each calculation was, and asks about the electrons of the session.
 *
 * @param link the connection
 */
export function useCalculation({ socketRef, held, note }: SessionLink): CalculationInfo {
  const [lastCalculation, setLastCalculation] = useState<CalculationResult | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<CalculationResult>("calculation.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setLastCalculation(result);
    });
  }, [held, socketRef]);

  const requestElectrons = useCallback(async () => {
    const socket = socketRef.current;
    const base = held.current;
    if (!socket || !base) {
      return null;
    }

    try {
      return await socket.requestElectrons(base.session_id);
    } catch (cause: unknown) {
      note(reason(cause), true);
      return null;
    }
  }, [held, note, socketRef]);

  const resetCalculations = useCallback(async () => {
    const socket = socketRef.current;
    const base = held.current;
    if (!socket || !base) {
      return false;
    }

    try {
      await socket.resetCalculations(base.session_id);
      return true;
    } catch (cause: unknown) {
      note(reason(cause), true);
      return false;
    }
  }, [held, note, socketRef]);

  return { lastCalculation, requestElectrons, resetCalculations };
}
