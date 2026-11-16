import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {StateIndex} from "../geometry/StateIndex";
import {describeStrain} from "../layout/findings";
import type {GeometryResult} from "../session/messages";
import {eulerOf, withEuler} from "../session/pose";
import {useSessionContext} from "../session/SessionContext";
import type {Pivot} from "../models/ToolModel";
import {NumberField} from "../controls/NumberField";
import {MoleculeDetails} from "./MoleculeDetails";
import {Property, signed} from "./paneParts";

interface MoleculePaneProps {
  id: string;
  index: StateIndex;
  /** What the server last said about the geometry, from which this molecule's strain is worked out. */
  geometry: GeometryResult | null;
  models: Models;
}

const PIVOTS: { value: Pivot; label: string }[] = [
  { value: "anchor", label: "Orbit anchor" },
  { value: "centre", label: "Centre of atoms" },
  { value: "origin", label: "Molecule origin" },
  { value: "point", label: "A point" },
];

/** Detail about one molecule of the session. */
export function MoleculePane({ id, index, geometry, models }: MoleculePaneProps) {
  const { moveMolecule } = useSessionContext();
  const { tools } = models;
  const { pivot, point } = useObservable(tools);
  const fragment = index.fragments.get(id);
  if (!fragment) {
    return null;
  }
  const charge = fragment.atoms.reduce((sum, atom) => sum + (index.atoms.get(atom)?.charge ?? 0), 0);
  const pose = index.state.transform;
  const angles = eulerOf(pose);
  return (
    <>
      <h3 className="pane-title">Molecule</h3>
      <dl className="properties">
        <Property name="Name" value={fragment.name} />
      </dl>

      <h4 className="pane-subtitle">Chemistry</h4>
      <MoleculeDetails state={index.state} models={models} fragment={fragment}>
        <Property name="Atoms" value={String(fragment.atoms.length)} />
        <Property name="Bonds" value={String(index.bondsInFragment(id).length)} />
        <Property name="Charge" value={signed(charge)} />
      </MoleculeDetails>

      <h4 className="pane-subtitle">Geometry</h4>
      <dl className="properties">
        <Property name="Strain" value={describeStrain(index.state, geometry, new Set(fragment.atoms))} />
        <dt>Position</dt>
        <dd className="position">
          {(["x", "y", "z"] as const).map((axis) => (
            <NumberField
              key={axis}
              label={`${axis.toUpperCase()} position of the molecule's origin, in ångströms`}
              value={pose.position[axis]}
              onCommit={(value) => void moveMolecule({ ...pose, position: { ...pose.position, [axis]: value } })}
            />
          ))}
          <span className="unit">Å</span>
        </dd>
        <dt>Rotation</dt>
        <dd className="position">
          {(["x", "y", "z"] as const).map((axis) => (
            <NumberField
              key={axis}
              label={`Rotation of the molecule about ${axis}, in degrees`}
              value={angles[axis]}
              digits={2}
              onCommit={(value) => void moveMolecule(withEuler(pose, { ...angles, [axis]: value }))}
            />
          ))}
          <span className="unit">°</span>
        </dd>
        <dt>Pivot</dt>
        <dd>
          <select
            value={pivot}
            aria-label="What the molecule turns about"
            title="What the handles turn the molecule about, and where they sit. The fields above always turn it about its own origin"
            onChange={(event) => tools.pivotOn(event.target.value as Pivot)}
          >
            {PIVOTS.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </dd>
        {pivot === "point" && (
          <>
            <dt>Pivot point</dt>
            <dd className="position">
              {(["x", "y", "z"] as const).map((axis) => (
                <NumberField
                  key={axis}
                  label={`${axis.toUpperCase()} of the pivot point, in ångströms`}
                  value={point[axis]}
                  onCommit={(value) => tools.setPoint({ ...point, [axis]: value })}
                />
              ))}
              <span className="unit">Å</span>
            </dd>
          </>
        )}
      </dl>
      {index.state.fragments.length > 1 && <p className="panel-note">The session holds one molecule, so moving this part moves all of them together.</p>}
    </>
  );
}
