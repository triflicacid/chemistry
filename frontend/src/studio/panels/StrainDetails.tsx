import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {StateIndex} from "../geometry/StateIndex";
import type {GeometryResult} from "../session/messages";
import {describeFinding, isCurrent} from "../layout/findings";
import {Property} from "./paneParts";

interface StrainDetailsProps {
  /** The atoms of what is selected: the atom, or the two ends of a bond. */
  atoms: string[];
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
}

/** What is wrong with the selected atom or bond, while strain is shown. */
export function StrainDetails({ atoms, index, geometry, models }: StrainDetailsProps) {
  const { strain } = useObservable(models.tools);
  if (!strain) {
    return null;
  }

  const title = <h4 className="pane-subtitle">Strain</h4>;
  if (!geometry || geometry.status === "failed") {
    return (
      <>
        {title}
        <p className="panel-empty">The geometry has not been examined.</p>
      </>
    );
  }
  if (!isCurrent(geometry, index.state)) {
    return (
      <>
        {title}
        <p className="panel-empty">Working it out…</p>
      </>
    );
  }

  const findings = geometry.findings.filter((finding) => atoms.every((id) => finding.atoms.includes(id)));
  const share = geometry.atom_strain.find((each) => each.id === atoms[0])?.strain ?? 0;
  return (
    <>
      {title}
      {atoms.length === 1 && (
        <dl className="properties">
          <Property name="Share" value={share.toFixed(2)} mono />
        </dl>
      )}
      {findings.length === 0 ? (
        <p className="panel-empty">Nothing beyond its tolerance.</p>
      ) : (
        <ul className="findings">
          {findings.map((finding) => (
            <li key={`${finding.kind}:${finding.atoms.join(",")}`}>
              <span className="finding">{describeFinding(finding, (id) => index.label(id))}</span>
              <span className="severity">{finding.severity.toFixed(1)}×</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
