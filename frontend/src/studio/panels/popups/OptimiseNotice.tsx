import type {OptimisationOutcome} from "../../session/types";
import {describeOutcome} from "../optimisation";
import {Popup} from "./Popup";

const LINGER_MS = 10_000;

export interface OptimiseNoticeProps {
  outcome: OptimisationOutcome | null;
}

/** Tells the user how an optimisation went. */
export function OptimiseNotice({ outcome }: OptimiseNoticeProps) {
  if (!outcome) {
    return null;
  }
  const described = describeOutcome(outcome);
  return (
    <Popup key={outcome.at} severity={described.tone} fades={described.tone !== "bad"} lingerMs={LINGER_MS}>
      <strong>{described.headline}</strong>
      {described.detail && <span>{described.detail}</span>}
      {outcome.warnings.map((warning) => (
        <span key={warning}>Optimiser: {warning}</span>
      ))}
    </Popup>
  );
}
