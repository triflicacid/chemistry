import type {GeometryFinding, GeometryResult, SessionState} from "../session/messages";

/**
 * Says what is wrong in a line, with atoms named as the rest of Studio names them.
 *
 * @param finding the term beyond its tolerance
 * @param label names an atom from its identifier
 */
export function describeFinding(finding: GeometryFinding, label: (id: string) => string): string {
  const atoms = finding.atoms.map(label);
  switch (finding.kind) {
    case "bond":
      return `Bond ${atoms.join("–")}  ${finding.actual.toFixed(3)} Å, ideal ${finding.ideal.toFixed(3)} Å`;
    case "angle":
      return `Angle ${atoms.join("–")}  ${finding.actual.toFixed(1)}°, ideal ${finding.ideal.toFixed(1)}°`;
    default:
      return `Clash ${atoms.join("…")}  ${finding.actual.toFixed(2)} Å, contact ${finding.ideal.toFixed(2)} Å`;
  }
}

/**
 * Whether a result describes the session as it is now.
 *
 * @param geometry what the server last said of the geometry
 * @param state the session's state
 */
export function isCurrent(geometry: GeometryResult | null, state: SessionState | null): boolean {
  return geometry !== null && state !== null && geometry.session_id === state.session_id && geometry.version === state.version;
}
