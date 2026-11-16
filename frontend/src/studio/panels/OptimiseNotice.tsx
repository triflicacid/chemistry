import {useEffect, useState} from "react";
import type {OptimisationOutcome} from "../session/useSession";
import {describeOutcome} from "./optimisation";

/** How long a notice that is not a failure stays before it fades. */
const LINGER_MS = 10_000;

export interface OptimiseNoticeProps {
  /** How the latest optimisation ended, or null if none has. */
  outcome: OptimisationOutcome | null;
}

/** Tells the user how an optimisation went. */
export function OptimiseNotice({ outcome }: OptimiseNoticeProps) {
  // each outcome is a fresh notice, so having dismissed the last one says nothing about this one
  return outcome ? <Notice key={outcome.at} outcome={outcome} /> : null;
}

function Notice({ outcome }: { outcome: OptimisationOutcome }) {
  const [shown, setShown] = useState(true);
  const described = describeOutcome(outcome);

  useEffect(() => {
    if (described.tone === "bad") {
      return;
    }
    const timer = setTimeout(() => setShown(false), LINGER_MS);
    return () => clearTimeout(timer);
  }, [described.tone]);

  if (!shown) {
    return null;
  }

  return (
    <div className={`optimise-notice optimise-${described.tone}`} role="status">
      <div className="optimise-text">
        <strong>{described.headline}</strong>
        {described.detail && <span>{described.detail}</span>}
        {outcome.warnings.map((warning) => (
          <span key={warning}>Optimiser: {warning}</span>
        ))}
      </div>
      <button type="button" className="link" aria-label="Dismiss" title="Dismiss" onClick={() => setShown(false)}>
        ✕
      </button>
    </div>
  );
}
