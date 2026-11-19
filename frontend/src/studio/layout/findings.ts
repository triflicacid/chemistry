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
 * @param state the session
 * @param geometry what the server last said of its geometry
 * @param atoms the atoms of the part to describe, or undefined for the whole molecule
 */
export function describeStrain(state: SessionState, geometry: GeometryResult | null, atoms?: ReadonlySet<string>): string {
  if (!geometry) {
    return "not examined yet";
  }
  if (geometry.status === "failed" || geometry.strain === null) {
    return "unavailable";
  }

  const wait = isCurrent(geometry, state) ? "" : " (updating…)";
  const strain = atoms
    ? Math.sqrt(geometry.atom_strain.filter((share) => atoms.has(share.id)).reduce((sum, share) => sum + share.strain * share.strain, 0))
    : geometry.strain;
  const strained = atoms ? geometry.findings.some((finding) => finding.atoms.every((id) => atoms.has(id))) : geometry.findings.length > 0;
  return `${strain.toFixed(2)} · ${strained ? "strained" : "happy"}${wait}`;
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
