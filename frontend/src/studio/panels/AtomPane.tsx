import {useEffect, useState} from "react";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {StateIndex} from "../geometry/StateIndex";
import type {GeometryResult, SessionAtom} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";
import {NumberField} from "../controls/NumberField";
import {type Segment, Segmented} from "../controls/Segmented";
import type {Hold} from "../geometry/bondLength";
import {heaviestFirst} from "../geometry/torsion";
import {angleAt, angleMovers, choosePair} from "../geometry/angle";
import {Links, Property, signed} from "./paneParts";
import {RingMemberships} from "./RingMemberships";
import {StrainDetails} from "./StrainDetails";

interface AtomPaneProps {
  atom: SessionAtom;
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
}

/** Describe a single atom. */
export function AtomPane({ atom, index, geometry, models }: AtomPaneProps) {
  const { moveAtom, lockAtoms } = useSessionContext();
  const { position } = atom;
  const fragment = index.fragmentOf({ kind: "atom", id: atom.id });
  const state = index.state;
  const locked = state.locked.includes(atom.id);
  return (
    <>
      <h3 className="pane-title">Atom {index.label(atom.id)}</h3>
      <dl className="properties">
        <Property name="ID" value={atom.id} mono />
      </dl>

      <h4 className="pane-subtitle">Chemistry</h4>
      <dl className="properties">
        <Property name="Element" value={atom.element} />
        {atom.isotope > 0 && <Property name="Isotope" value={String(atom.isotope)} />}
        <Property name="Charge" value={signed(atom.charge)} />
        <Property name="Aromatic" value={atom.aromatic ? "yes" : "no"} />
        <Property name="Radius" value={atom.radius.toFixed(3) + " Å"} />
        {atom.was_implicit && <Property name="Origin" value="was an implicit hydrogen" />}
        {fragment !== null && <Property name="Molecule" value={index.fragments.get(fragment)?.name ?? "?"} />}
      </dl>

      <h4 className="pane-subtitle">Geometry</h4>
      <dl className="properties">
        <dt>Position</dt>
        <dd className="position">
          {(["x", "y", "z"] as const).map((axis) => (
            <NumberField
              key={axis}
              label={`${axis.toUpperCase()} position of ${index.label(atom.id)}, in ångströms`}
              value={position[axis]}
              disabled={locked}
              onCommit={(value) => void moveAtom(atom.id, { ...position, [axis]: value })}
            />
          ))}
          <span className="unit">Å</span>
        </dd>
        <dt>Locked</dt>
        <dd>
          <input
            type="checkbox"
            checked={locked}
            aria-label={`Lock ${index.label(atom.id)} against edits`}
            title="A locked atom cannot be moved, by dragging or by typing a position"
            onChange={(event) => void lockAtoms([atom.id], event.target.checked)}
          />
        </dd>
      </dl>

      <AngleRows centre={atom.id} index={index} models={models} />

      <RingMemberships kind="atom" id={atom.id} atoms={[atom.id]} models={models} />

      <h4 className="pane-subtitle">Bonded to</h4>
      <Links
        models={models}
        links={index.neighboursOf(atom.id).map((id) => ({ item: { kind: "atom", id }, label: index.label(id) }))}
      />

      <h4 className="pane-subtitle">Bonds</h4>
      <Links models={models} links={index.bondsOf(atom.id).map((bond) => ({ item: { kind: "bond", id: bond.id }, label: index.bondLabel(bond) }))} />

      <StrainDetails atoms={[atom.id]} index={index} geometry={geometry} models={models} />
    </>
  );
}

interface AngleRowsProps {
  centre: string;
  index: StateIndex;
  models: Models;
}

/** The rows that give an atom's two bonds an angle. */
function AngleRows({ centre, index, models }: AngleRowsProps) {
  const { setAngle } = useSessionContext();
  const { tools } = models;
  const { hold } = useObservable(tools);
  const [chosen, setChosen] = useState<{ from?: string; to?: string }>({});
  const [refusal, setRefusal] = useState<string | null>(null);
  const neighbours = heaviestFirst(index, index.neighboursOf(centre));
  const pair = choosePair(index, centre, chosen);

  // the viewport marks the angle shown here, and stops when the rows go
  const markedFrom = pair?.from;
  const markedTo = pair?.to;
  useEffect(() => {
    tools.showAngle(markedFrom && markedTo ? { centre, from: markedFrom, to: markedTo } : null);
    return () => tools.showAngle(null);
  }, [tools, centre, markedFrom, markedTo]);

  const a = pair && index.atoms.get(pair.from);
  const b = index.atoms.get(centre);
  const c = pair && index.atoms.get(pair.to);
  if (!pair || !a || !b || !c) {
    return null;
  }

  const heldAtom = hold === "first" ? pair.from : hold === "second" ? pair.to : null;
  const holds: Segment<Hold>[] = [
    { value: "first", label: index.label(pair.from), title: `Keep ${index.label(pair.from)} where it is and swing ${index.label(pair.to)}` },
    { value: "second", label: index.label(pair.to), title: `Keep ${index.label(pair.to)} where it is and swing ${index.label(pair.from)}` },
    { value: "both", label: "Neither", title: "Swing both, half the change each, so the line that bisects the angle stays put" },
  ];
  const inRing = angleMovers(index, centre, pair, "both") === null;
  const select = (value: string, label: string, onChange: (id: string) => void) => (
    <select value={value} aria-label={label} onChange={(event) => onChange(event.target.value)}>
      {neighbours.map((id) => (
        <option key={id} value={id}>
          {index.label(id)}
        </option>
      ))}
    </select>
  );

  return (
    <>
      <h4 className="pane-subtitle">Angle</h4>
      <dl className="properties">
      <dt>Angle</dt>
      <dd className="position">
        <NumberField
          label={`Angle at ${index.label(centre)} between ${index.label(pair.from)} and ${index.label(pair.to)}, in degrees`}
          value={angleAt(a.position, b.position, c.position)}
          digits={1}
          onCommit={(value) => void setAngle(centre, pair.from, pair.to, value, heldAtom).then((result) => setRefusal(result.kind === "refused" ? result.message : null))}
        />
        <span className="unit">°</span>
      </dd>
      <dt>Between</dt>
      <dd className="position">
        {select(pair.from, `One atom the angle at ${index.label(centre)} is measured to`, (id) => setChosen({ ...chosen, from: id }))}
        <span className="unit">and</span>
        {select(pair.to, `The other atom the angle at ${index.label(centre)} is measured to`, (id) => setChosen({ ...chosen, to: id }))}
      </dd>
      <dt>In ring</dt>
      <dd title="An angle inside a ring cannot swing both sides without tearing it, so hold one atom">{inRing ? "yes" : "no"}</dd>
      <dt>Hold</dt>
      <dd>
        <Segmented label="Which atom of the angle stays where it is" value={hold} options={holds} onChange={(next) => tools.holding(next)} />
      </dd>
      <dd className="note-row">
        {refusal !== null && <p className="panel-note">{refusal}</p>}
      </dd>
      </dl>
    </>
  );
}
