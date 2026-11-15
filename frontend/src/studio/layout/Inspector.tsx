import {type MouseEvent, type ReactNode, useState} from "react";
import {type Item, keyOf} from "../selection/items";
import type {Models} from "../selection/models";
import {useObservable} from "../selection/observable";
import type {StateIndex} from "../selection/StateIndex";
import type {Vec3} from "../../api/types/structure";
import type {AtomMove, GeometryResult, Pose, SessionAtom, SessionBond, SessionState} from "../session/messages";
import {eulerOf, withEuler} from "../session/pose";
import type {Pivot} from "../selection/ToolModel";
import {describeFinding, isCurrent} from "./findings";
import {NumberField} from "./NumberField";
import {type Segment, Segmented} from "./Segmented";
import {type Hold, lengthEdit, sideOf} from "../selection/bondLength";

export interface InspectorProps {
  state: SessionState | null;
  /** What the server last said about the geometry, which is detailed for the selected atom or bond while strain is shown. */
  geometry: GeometryResult | null;
  models: Models;
  /** Puts an atom at a position. */
  onMoveAtom: (id: string, position: Vec3) => void;
  /** Puts several atoms at positions, as one edit. */
  onMoveAtoms: (moves: AtomMove[]) => void;
  /** Puts the whole molecule at a pose. */
  onMoveMolecule: (pose: Pose) => void;
  /** Locks or unlocks atoms. */
  onLockAtoms: (ids: string[], locked: boolean) => void;
}

/**
 * The properties of whatever is selected. An atom's position, and where a molecule sits, can be edited.
 *
 * The active item decides what is shown. With nothing selected it shows the session; with a
 * mixture of kinds it says what the mixture is and does not guess at a pane.
 */
export function Inspector({ state, geometry, models, onMoveAtom, onMoveAtoms, onMoveMolecule, onLockAtoms }: InspectorProps) {
  const { selection, index } = models;
  const { items, active } = useObservable(selection);

  if (!state || !index) {
    return <p className="panel-empty">Nothing is open.</p>;
  }

  if (items.length === 0 || !active) {
    return (
      <>
        <p className="panel-empty">Nothing selected.</p>
        <SessionPane state={state} geometry={geometry} />
      </>
    );
  }

  if (new Set(items.map((item) => item.kind)).size > 1) {
    return <MixedPane items={items} active={active} index={index} />;
  }

  return (
    <>
      {items.length > 1 && (
        <p className="panel-note">
          {items.length} {active.kind}s selected. Showing {describe(active, index)}.
        </p>
      )}
      <ItemPane
        item={active}
        index={index}
        geometry={geometry}
        models={models}
        onMoveAtom={onMoveAtom}
        onMoveAtoms={onMoveAtoms}
        onMoveMolecule={onMoveMolecule}
        onLockAtoms={onLockAtoms}
      />
    </>
  );
}

interface ItemPaneProps {
  item: Item;
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
  onMoveAtom: (id: string, position: Vec3) => void;
  onMoveAtoms: (moves: AtomMove[]) => void;
  onMoveMolecule: (pose: Pose) => void;
  onLockAtoms: (ids: string[], locked: boolean) => void;
}

function ItemPane({ item, index, geometry, models, onMoveAtom, onMoveAtoms, onMoveMolecule, onLockAtoms }: ItemPaneProps) {
  switch (item.kind) {
    case "atom": {
      const atom = index.atoms.get(item.id);
      return atom ? <AtomPane key={atom.id} atom={atom} index={index} geometry={geometry} models={models} onMove={onMoveAtom} onLock={onLockAtoms} /> : null;
    }
    case "bond": {
      const bond = index.bonds.get(item.id);
      return bond ? <BondPane key={bond.id} bond={bond} index={index} geometry={geometry} models={models} onMove={onMoveAtoms} /> : null;
    }
    default:
      return <MoleculePane id={item.id} index={index} models={models} onMove={onMoveMolecule} />;
  }
}

function SessionPane({ state, geometry }: { state: SessionState; geometry: GeometryResult | null }) {
  return (
    <dl className="properties">
      <Property name="SMILES" value={state.smiles} mono />
      <Property name="Atoms" value={String(state.atoms.length)} />
      <Property name="Bonds" value={String(state.bonds.length)} />
      <Property name="Molecules" value={String(state.fragments.length)} />
      <Property name="Strain" value={strainOf(state, geometry)} />
      <Property name="Version" value={String(state.version)} />
      <Property name="Source" value={state.source_molecule_id} mono />
      <Property name="Saved" value={state.saved_molecule_id ?? "not saved"} mono={state.saved_molecule_id !== null} />
    </dl>
  );
}

/**
 * Says how strained the molecule is as a whole.
 *
 * @param state the session
 * @param geometry what the server last said of its geometry
 */
function strainOf(state: SessionState, geometry: GeometryResult | null): string {
  if (!geometry) {
    return "not examined yet";
  }
  if (geometry.status === "failed" || geometry.strain === null) {
    return "unavailable";
  }

  const wait = isCurrent(geometry, state) ? "" : " (updating…)";
  return `${geometry.strain.toFixed(2)} · ${geometry.findings.length === 0 ? "happy" : "strained"}${wait}`;
}

interface MixedPaneProps {
  items: readonly Item[];
  active: Item;
  index: StateIndex;
}

function MixedPane({ items, active, index }: MixedPaneProps) {
  const count = (kind: Item["kind"]) => items.filter((item) => item.kind === kind).length;
  return (
    <>
      <p className="panel-note">Mixed selection: select one kind of thing to see its properties.</p>
      <dl className="properties">
        <Property name="Molecules" value={String(count("molecule"))} />
        <Property name="Atoms" value={String(count("atom"))} />
        <Property name="Bonds" value={String(count("bond"))} />
        <Property name="Active" value={describe(active, index)} />
      </dl>
    </>
  );
}

interface MoleculePaneProps {
  id: string;
  index: StateIndex;
  models: Models;
  onMove: (pose: Pose) => void;
}

const PIVOTS: { value: Pivot; label: string }[] = [
  { value: "anchor", label: "Orbit anchor" },
  { value: "centre", label: "Centre of atoms" },
  { value: "origin", label: "Molecule origin" },
  { value: "point", label: "A point" },
];

function MoleculePane({ id, index, models, onMove }: MoleculePaneProps) {
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
        <Property name="Formula" value={index.formula(id)} mono />
        <Property name="Atoms" value={String(fragment.atoms.length)} />
        <Property name="Bonds" value={String(index.bondsInFragment(id).length)} />
        <Property name="Charge" value={signed(charge)} />
        <dt>Position</dt>
        <dd className="position">
          {(["x", "y", "z"] as const).map((axis) => (
            <NumberField
              key={axis}
              label={`${axis.toUpperCase()} position of the molecule's origin, in ångströms`}
              value={pose.position[axis]}
              onCommit={(value) => onMove({ ...pose, position: { ...pose.position, [axis]: value } })}
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
              onCommit={(value) => onMove(withEuler(pose, { ...angles, [axis]: value }))}
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

interface AtomPaneProps {
  atom: SessionAtom;
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
  onMove: (id: string, position: Vec3) => void;
  onLock: (ids: string[], locked: boolean) => void;
}

function AtomPane({ atom, index, geometry, models, onMove, onLock }: AtomPaneProps) {
  const { position } = atom;
  const fragment = index.fragmentOf({ kind: "atom", id: atom.id });
  const state = index.state;
  const locked = state.locked.includes(atom.id);
  return (
    <>
      <h3 className="pane-title">Atom {index.label(atom.id)}</h3>
      <dl className="properties">
        <Property name="ID" value={atom.id} mono />
        <Property name="Element" value={atom.element} />
        {atom.isotope > 0 && <Property name="Isotope" value={String(atom.isotope)} />}
        <Property name="Charge" value={signed(atom.charge)} />
        <Property name="Aromatic" value={atom.aromatic ? "yes" : "no"} />
        <dt>Position</dt>
        <dd className="position">
          {(["x", "y", "z"] as const).map((axis) => (
            <NumberField
              key={axis}
              label={`${axis.toUpperCase()} position of ${index.label(atom.id)}, in ångströms`}
              value={position[axis]}
              disabled={locked}
              onCommit={(value) => onMove(atom.id, { ...position, [axis]: value })}
            />
          ))}
          <span className="unit">Å</span>
        </dd>
        {atom.was_implicit && <Property name="Origin" value="was an implicit hydrogen" />}
        <dt>Locked</dt>
        <dd>
          <input
            type="checkbox"
            checked={locked}
            aria-label={`Lock ${index.label(atom.id)} against edits`}
            title="A locked atom cannot be moved, by dragging or by typing a position"
            onChange={(event) => onLock([atom.id], event.target.checked)}
          />
        </dd>
        {fragment !== null && <Property name="Molecule" value={index.fragments.get(fragment)?.name ?? "?"} />}
      </dl>

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

interface BondPaneProps {
  bond: SessionBond;
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
  onMove: (moves: AtomMove[]) => void;
}

function BondPane({ bond, index, geometry, models, onMove }: BondPaneProps) {
  const [hold, setHold] = useState<Hold>("first");
  const [refusal, setRefusal] = useState<string | null>(null);
  const holds: Segment<Hold>[] = [
    { value: "first", label: index.label(bond.first), title: `Keep ${index.label(bond.first)} where it is and move ${index.label(bond.second)}` },
    { value: "second", label: index.label(bond.second), title: `Keep ${index.label(bond.second)} where it is and move ${index.label(bond.first)}` },
    { value: "both", label: "Neither", title: "Move both atoms, half the change each, so the middle of the bond stays put" },
  ];
  const first = index.atoms.get(bond.first);
  const second = index.atoms.get(bond.second);
  const length = first && second ? Math.hypot(first.position.x - second.position.x, first.position.y - second.position.y, first.position.z - second.position.z) : null;
  return (
    <>
      <h3 className="pane-title">Bond {index.bondLabel(bond)}</h3>
      <dl className="properties">
        <Property name="ID" value={bond.id} mono />
        <Property name="Order" value={bond.order.toLowerCase()} />
        <Property name="Aromatic" value={bond.aromatic ? "yes" : "no"} />
        {length !== null && (
          <>
            <dt>Length</dt>
            <dd className="position">
              <NumberField
                label={`Length of ${index.bondLabel(bond)}, in ångströms`}
                value={length}
                onCommit={(value) => {
                  const edit = lengthEdit(index, bond.id, value, hold, index.state.locked);
                  setRefusal(edit.kind === "refused" ? edit.reason : null);
                  if (edit.kind === "moves") {
                    onMove(edit.moves);
                  }
                }}
              />
              <span className="unit">Å</span>
            </dd>
            <dt>Hold</dt>
            <dd>
              <Segmented label="Which atom of the bond stays where it is" value={hold} options={holds} onChange={setHold} />
            </dd>
          </>
        )}
      </dl>
      {refusal !== null && <p className="panel-note">{refusal}</p>}
      <p className="panel-note">{lengthNote(index, bond.id)}</p>

      <h4 className="pane-subtitle">Joins</h4>
      <Links
        models={models}
        links={[bond.first, bond.second].map((id) => ({ item: { kind: "atom", id }, label: index.label(id) }))}
      />

      <StrainDetails atoms={[bond.first, bond.second]} index={index} geometry={geometry} models={models} />
    </>
  );
}

/**
 * Says what changing a bond's length would do to the rest of the molecule.
 *
 * @param index the session's atoms and bonds
 * @param id the bond
 * @returns a sentence for the user
 */
function lengthNote(index: StateIndex, id: string): string {
  const bond = index.bonds.get(id);
  if (bond && sideOf(index, bond, bond.first) === null) {
    return "This bond is in a ring, so only its two atoms move and the rest of the ring is left behind. Optimise afterwards to relax it.";
  }
  return "Everything beyond the atom that moves goes with it, so no other length or angle changes.";
}

interface StrainDetailsProps {
  /** The atoms of what is selected: the atom, or the two ends of a bond. */
  atoms: string[];
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
}

/**
 * What is wrong with the selected atom or bond, while strain is shown: how much of the strain is the atom's, and every
 * bond, angle and clash beyond its tolerance that involves all of its atoms.
 */
function StrainDetails({ atoms, index, geometry, models }: StrainDetailsProps) {
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

interface LinksProps {
  models: Models;
  links: { item: Item; label: string }[];
}

/** A row of buttons that each name an item: hovering one highlights it, and clicking selects it. */
function Links({ models, links }: LinksProps) {
  const { selection, hover } = models;
  if (links.length === 0) {
    return <p className="panel-empty">None.</p>;
  }
  return (
    <ul className="links">
      {links.map(({ item, label }) => (
        <li key={keyOf(item)}>
          <button
            type="button"
            className="link"
            onClick={(event) => selection.select(item, additive(event))}
            onPointerEnter={() => hover.hover(item)}
            onPointerLeave={() => hover.hover(null)}
          >
            {label}
          </button>
        </li>
      ))}
    </ul>
  );
}

interface PropertyProps {
  name: string;
  value: ReactNode;
  mono?: boolean;
}

function Property({ name, value, mono = false }: PropertyProps) {
  return (
    <>
      <dt>{name}</dt>
      <dd className={mono ? "mono" : undefined} title={typeof value === "string" ? value : undefined}>
        {value}
      </dd>
    </>
  );
}

function additive(event: MouseEvent): boolean {
  return event.shiftKey || event.ctrlKey || event.metaKey;
}

/** What to call an item: an atom or bond by its label, a molecule by its name. */
function describe(item: Item, index: StateIndex): string {
  switch (item.kind) {
    case "atom":
      return index.label(item.id);
    case "bond": {
      const bond = index.bonds.get(item.id);
      return bond ? index.bondLabel(bond) : "?";
    }
    default:
      return index.fragments.get(item.id)?.name ?? "?";
  }
}

/** Writes a charge with its sign, so zero is `0`, one is `+1` and minus one is `-1`. */
function signed(charge: number): string {
  return charge > 0 ? `+${charge}` : String(charge);
}
