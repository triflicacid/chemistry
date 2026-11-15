import {useEffect, useRef, useState} from "react";
import type {Models} from "../selection/models";
import type {GeometryResult, SessionState} from "../session/messages";
import type {Activity, OptimisationOutcome} from "../session/useSession";
import {describeFinding, isCurrent} from "./findings";
import {describeOutcome} from "./optimisation";
import {type Segment, Segmented} from "./Segmented";

export interface AnalysisPanelProps {
  activity: Activity[];
  state: SessionState | null;
  /** What the server last said about the geometry. */
  geometry: GeometryResult | null;
  /** How the latest optimisation ended, or null if there has been none. */
  outcome: OptimisationOutcome | null;
  models: Models;
}

type Tab = "geometry" | "activity";

const TABS: Segment<Tab>[] = [
  { value: "geometry", label: "Geometry" },
  { value: "activity", label: "Activity" },
];

/** How strained the geometry is and what is wrong with it, and what the session has done. */
export function AnalysisPanel({ activity, state, geometry, outcome, models }: AnalysisPanelProps) {
  const [tab, setTab] = useState<Tab>("geometry");

  return (
    <>
      <div className="analysis-tabs">
        <Segmented label="Analysis" value={tab} options={TABS} onChange={setTab} />
      </div>
      {tab === "geometry" ? (
        <>
          {outcome && <LastOptimisation outcome={outcome} />}
          <GeometryView state={state} geometry={geometry} models={models} />
        </>
      ) : (
        <ActivityLog activity={activity} />
      )}
    </>
  );
}

/** How the latest optimisation went, kept here after the notice over the viewport has faded. */
function LastOptimisation({ outcome }: { outcome: OptimisationOutcome }) {
  const described = describeOutcome(outcome);

  return (
    <div className={`last-optimisation optimise-${described.tone}`}>
      <strong>{described.headline}</strong>
      {described.detail && <span>{described.detail}</span>}
    </div>
  );
}

interface GeometryViewProps {
  state: SessionState | null;
  geometry: GeometryResult | null;
  models: Models;
}

/** The total strain and the worst things wrong, each of which selects its atoms when clicked. */
function GeometryView({ state, geometry, models }: GeometryViewProps) {
  const { selection, index } = models;

  if (!geometry) {
    return <p className="panel-empty">The geometry has not been examined yet.</p>;
  }
  if (geometry.status === "failed" || geometry.strain === null) {
    return <p className="panel-empty">The geometry could not be examined: {geometry.message ?? "no reason was given"}.</p>;
  }

  // a result describes the version it was made at, and the session moves on faster than the server can say how
  const current = isCurrent(geometry, state);
  const label = (id: string) => index?.label(id) ?? id;

  return (
    <div className="geometry">
      <p className="geometry-total">
        Strain <strong>{geometry.strain.toFixed(2)}</strong>
        {!current && <span className="geometry-stale"> (updating…)</span>}
      </p>
      {geometry.findings.length === 0 ? (
        <p className="panel-empty">Nothing is beyond its tolerance.</p>
      ) : (
        <ol className="findings" aria-label="Worst findings" data-stale={current ? undefined : "true"}>
          {geometry.findings.map((finding) => (
            <li key={`${finding.kind}:${finding.atoms.join(",")}`}>
              <button
                type="button"
                className="link finding"
                title="Select these atoms"
                onClick={() => selection.replace(finding.atoms.map((id) => ({ kind: "atom", id })))}
              >
                {describeFinding(finding, label)}
              </button>
              <span className="severity">{finding.severity.toFixed(1)}× tolerance</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

/** What the session has done, newest at the bottom. */
function ActivityLog({ activity }: { activity: Activity[] }) {
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
