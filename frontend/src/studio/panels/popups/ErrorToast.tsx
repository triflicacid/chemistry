import type {Activity} from "../../session/types";
import {Popup} from "./Popup";

export interface ErrorToastProps {
  activity: Activity[];
}

/** Tells the user of an error, or of a warning that something asked for cannot be done, then lets it fade. */
export function ErrorToast({ activity }: ErrorToastProps) {
  const latest = activity.at(-1);
  if (!latest?.failed && !latest?.warning) {
    return null;
  }
  return (
    <Popup key={`${latest.at}-${latest.message}`} severity={latest.failed ? "bad" : "warn"} fades>
      {latest.preformatted ? <pre>{latest.message}</pre> : latest.message}
    </Popup>
  );
}
