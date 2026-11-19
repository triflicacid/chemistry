import type {Activity} from "../../session/types";
import {Popup} from "./Popup";

export interface ErrorToastProps {
  activity: Activity[];
}

/** Tells the user of an error, then lets it fade. */
export function ErrorToast({ activity }: ErrorToastProps) {
  const latest = activity.at(-1);
  if (!latest?.failed) {
    return null;
  }
  return (
    <Popup key={`${latest.at}-${latest.message}`} severity="bad" fades>
      {latest.preformatted ? <pre>{latest.message}</pre> : latest.message}
    </Popup>
  );
}
