import {useCallback, useEffect, useState} from "react";
import type {ScanResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";

export interface Scan {
  /** The scan as the server last said, or null if it has not. It describes the version in `version`. */
  scan: ScanResult | null;
  /** When the calculation under way was asked for, as {@link Date.now} says, or null if there is none. */
  scanningSince: number | null;
  /** Asks the server to scan the session as it is now. */
  requestScan: () => void;
}

/**
 * Keeps the scan of a hydrogen bond that the server sends, and asks for it.
 *
 * @param link the connection
 */
export function useScan({ socketRef, held, note }: SessionLink): Scan {
  const [scan, setScan] = useState<ScanResult | null>(null);
  const [scanningSince, setScanningSince] = useState<number | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<ScanResult>("scan.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setScanningSince(null);
      setScan(result);
    });
  }, [held, socketRef]);

  const requestScan = useCallback(() => {
    const socket = socketRef.current;
    const base = held.current;
    if (!socket || !base) {
      return;
    }

    setScanningSince(Date.now());
    socket.requestScan(base.session_id).catch((cause: unknown) => {
      setScanningSince(null);
      note(reason(cause), true);
    });
  }, [held, note, socketRef]);

  return { scan, scanningSince, requestScan };
}
