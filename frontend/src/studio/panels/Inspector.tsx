import type {Item} from "../models/items";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {StateIndex} from "../geometry/StateIndex";
import type {GeometryResult, SessionState} from "../session/messages";
import {AtomPane} from "./AtomPane";
import {BondPane} from "./BondPane";
import {CameraPane} from "./CameraPane";
import {MoleculePane} from "./MoleculePane";
import {Property} from "./paneParts";
import {SessionPane} from "./SessionPane";

export interface InspectorProps {
  state: SessionState | null;
  /** What the server last said about the geometry, which is detailed for the selected atom or bond while strain is shown. */
  geometry: GeometryResult | null;
  models: Models;
}

/**
 * The properties of whatever is selected. An atom's position, and where a molecule sits, can be edited.
 *
 * The active item decides what is shown. With nothing selected it shows the session; with a
 * mixture of kinds it says what the mixture is and does not guess at a pane.
 */
export function Inspector({ state, geometry, models }: InspectorProps) {
  const { selection, index } = models;
  const { items, active } = useObservable(selection);
  const { tool } = useObservable(models.tools);

  // panning moves the camera and picks nothing, so it is the camera that is shown
  if (tool === "pan") {
    return <CameraPane models={models} />;
  }

  if (!state || !index) {
    return <p className="panel-empty">Nothing is open.</p>;
  }

  if (items.length === 0 || !active) {
    return (
      <>
        <p className="panel-empty">Nothing selected.</p>
        <SessionPane state={state} geometry={geometry} models={models} />
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
      <ItemPane item={active} index={index} geometry={geometry} models={models} />
    </>
  );
}

interface ItemPaneProps {
  item: Item;
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
}

/** Picks the pane for a selected atom, bond or molecule. */
function ItemPane({ item, index, geometry, models }: ItemPaneProps) {
  switch (item.kind) {
    case "atom": {
      const atom = index.atoms.get(item.id);
      return atom ? <AtomPane key={atom.id} atom={atom} index={index} geometry={geometry} models={models} /> : null;
    }
    case "bond": {
      const bond = index.bonds.get(item.id);
      return bond ? <BondPane key={bond.id} bond={bond} index={index} geometry={geometry} models={models} /> : null;
    }
    default:
      return <MoleculePane id={item.id} index={index} models={models} />;
  }
}

interface MixedPaneProps {
  items: readonly Item[];
  active: Item;
  index: StateIndex;
}

/** Says what a selection of more than one kind of item is made of, since no one pane describes it. */
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
