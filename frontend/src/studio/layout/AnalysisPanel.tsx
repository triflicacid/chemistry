import { useEffect, useRef } from "react";
import type { Activity } from "../session/useSession";

export interface AnalysisPanelProps {
  activity: Activity[];
}

/** What the session has done, newest at the bottom. */
export function AnalysisPanel({ activity }: AnalysisPanelProps) {
  const end = useRef<HTMLOListElement | null>(null);

  useEffect(() => {
    const list = end.current;
    if (list) {
      list.scrollTop = list.scrollHeight;
    }
  }, [activity]);

  if (activity.length === 0) {
    return <p className="panel-empty">Nothing has happened yet.</p>;
  }

  return (
    <ol className="activity" ref={end}>
      {activity.map((line) => (
        <li key={`${line.at}-${line.message}`} className={line.failed ? "failed" : undefined}>
          <time>{clock(line.at)}</time>
          <pre>{line.message}</pre>
        </li>
      ))}
    </ol>
  );
}

function clock(at: number): string {
  return new Date(at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}
