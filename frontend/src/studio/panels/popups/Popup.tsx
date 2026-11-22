import {type ReactNode, useEffect, useState} from "react";

export type Severity = "good" | "neutral" | "warn" | "bad";

/** How long a popup that fades stays before it has gone. */
const LINGER_MS = 5_000;

export interface PopupProps {
  severity: Severity;
  /** Whether the popup goes of its own accord, rather than staying until it is dismissed. */
  fades: boolean;
  /** How long a popup that fades takes to go. */
  lingerMs?: number;
  children: ReactNode;
}

/** A temporary message over the corner of the viewport that the user can dismiss. */
export function Popup({ severity, fades, lingerMs = LINGER_MS, children }: PopupProps) {
  const [shown, setShown] = useState(true);

  useEffect(() => {
    if (!fades) {
      return;
    }
    const timer = setTimeout(() => setShown(false), lingerMs);
    return () => clearTimeout(timer);
  }, [fades, lingerMs]);

  if (!shown) {
    return null;
  }

  return (
    <div className={`popup popup-${severity}${fades ? " popup-fades" : ""}`} role={severity === "bad" ? "alert" : "status"} style={fades ? { animationDuration: `${lingerMs}ms` } : undefined}>
      <div className="popup-text">{children}</div>
      <button type="button" className="link" aria-label="Dismiss" title="Dismiss" onClick={() => setShown(false)}>
        ✕
      </button>
    </div>
  );
}
