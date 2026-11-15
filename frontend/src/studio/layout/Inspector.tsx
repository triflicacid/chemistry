import type { MouseEvent, ReactNode } from "react";
import { type Item, keyOf } from "../selection/items";
import type { Models } from "../selection/models";
import { useObservable } from "../selection/observable";
import type { StateIndex } from "../selection/StateIndex";
import type { SessionAtom, SessionBond, SessionState } from "../session/messages";

export interface InspectorProps {
  state: SessionState | null;
  models: Models;
}

/**
 * The properties of whatever is selected, read-only.
 *
 * The active item decides what is shown. With nothing selected it shows the session; with a
 * mixture of kinds it says what the mixture is and does not guess at a pane.
 */
export function Inspector({ state, models }: InspectorProps) {
  const { selection, index } = models;
  const { items, active } = useObservable(selection);

  if (!state || !index) {
    return <p className="panel-empty">Nothing is open.</p>;
  }

  if (items.length === 0 || !active) {
    return (
      <>
        <p className="panel-empty">Nothing selected.</p>
        <SessionPane state={state} />
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
      <ItemPane item={active} index={index} models={models} />
    </>
  );
}

interface ItemPaneProps {
  item: Item;
  index: StateIndex;
  models: Models;
}

function ItemPane({ item, index, models }: ItemPaneProps) {
  switch (item.kind) {
    case "atom": {
      const atom = index.atoms.get(item.id);
      return atom ? <AtomPane atom={atom} index={index} models={models} /> : null;
    }
    case "bond": {
      const bond = index.bonds.get(item.id);
      return bond ? <BondPane bond={bond} index={index} models={models} /> : null;
    }
    default:
      return <MoleculePane id={item.id} index={index} />;
  }
}

function SessionPane({ state }: { state: SessionState }) {
  return (
    <dl className="properties">
      <Property name="SMILES" value={state.smiles} mono />
      <Property name="Atoms" value={String(state.atoms.length)} />
      <Property name="Bonds" value={String(state.bonds.length)} />
      <Property name="Molecules" value={String(state.fragments.length)} />
      <Property name="Strain" value={state.strain.toFixed(3)} />
      <Property name="Version" value={String(state.version)} />
      <Property name="Source" value={state.source_molecule_id} mono />
      <Property name="Saved" value={state.saved_molecule_id ?? "not saved"} mono={state.saved_molecule_id !== null} />
    </dl>
  );
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

function MoleculePane({ id, index }: { id: string; index: StateIndex }) {
  const fragment = index.fragments.get(id);
  if (!fragment) {
    return null;
  }
  const charge = fragment.atoms.reduce((sum, atom) => sum + (index.atoms.get(atom)?.charge ?? 0), 0);
  return (
    <>
      <h3 className="pane-title">Molecule</h3>
      <dl className="properties">
        <Property name="Name" value={fragment.name} />
        <Property name="Formula" value={index.formula(id)} mono />
        <Property name="Atoms" value={String(fragment.atoms.length)} />
        <Property name="Bonds" value={String(index.bondsInFragment(id).length)} />
        <Property name="Charge" value={signed(charge)} />
      </dl>
    </>
  );
}

interface AtomPaneProps {
  atom: SessionAtom;
  index: StateIndex;
  models: Models;
}

function AtomPane({ atom, index, models }: AtomPaneProps) {
  const { x, y, z } = atom.position;
  const fragment = index.fragmentOf({ kind: "atom", id: atom.id });
  const state = index.state;
  return (
    <>
      <h3 className="pane-title">Atom {index.label(atom.id)}</h3>
      <dl className="properties">
        <Property name="ID" value={atom.id} mono />
        <Property name="Element" value={atom.element} />
        {atom.isotope > 0 && <Property name="Isotope" value={String(atom.isotope)} />}
        <Property name="Charge" value={signed(atom.charge)} />
        <Property name="Aromatic" value={atom.aromatic ? "yes" : "no"} />
        <Property name="Position" value={`${x.toFixed(3)}, ${y.toFixed(3)}, ${z.toFixed(3)} Å`} mono />
        {atom.was_implicit && <Property name="Origin" value="was an implicit hydrogen" />}
        <Property name="Pinned" value={state.pinned.includes(atom.id) ? "yes" : "no"} />
        <Property name="Locked" value={state.locked.includes(atom.id) ? "yes" : "no"} />
        {fragment !== null && <Property name="Molecule" value={index.fragments.get(fragment)?.name ?? "?"} />}
      </dl>

      <h4 className="pane-subtitle">Bonded to</h4>
      <Links
        models={models}
        links={index.neighboursOf(atom.id).map((id) => ({ item: { kind: "atom", id }, label: index.label(id) }))}
      />

      <h4 className="pane-subtitle">Bonds</h4>
      <Links models={models} links={index.bondsOf(atom.id).map((bond) => ({ item: { kind: "bond", id: bond.id }, label: index.bondLabel(bond) }))} />
    </>
  );
}

interface BondPaneProps {
  bond: SessionBond;
  index: StateIndex;
  models: Models;
}

function BondPane({ bond, index, models }: BondPaneProps) {
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
        {length !== null && <Property name="Length" value={`${length.toFixed(3)} Å`} mono />}
      </dl>

      <h4 className="pane-subtitle">Joins</h4>
      <Links
        models={models}
        links={[bond.first, bond.second].map((id) => ({ item: { kind: "atom", id }, label: index.label(id) }))}
      />
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
