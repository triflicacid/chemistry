import {useEffect, useState} from "react";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {StateIndex} from "../geometry/StateIndex";
import type {GeometryResult, SessionBond} from "../session/messages";
import type {EditResult} from "../session/useSession";
import {useSessionContext} from "../session/SessionContext";
import {NumberField} from "../controls/NumberField";
import {type Segment, Segmented} from "../controls/Segmented";
import {type Hold, sideOf} from "../geometry/bondLength";
import {defaultReference, referencesAt, torsion} from "../geometry/torsion";
import {Links, Property} from "./paneParts";
import {StrainDetails} from "./StrainDetails";

interface BondPaneProps {
  bond: SessionBond;
  index: StateIndex;
  geometry: GeometryResult | null;
  models: Models;
}

/** Details about a single bond. */
export function BondPane({ bond, index, geometry, models }: BondPaneProps) {
  const { setBondLength, setBondTorsion } = useSessionContext();
  const { tools } = models;
  // which atom stays is shared with the viewport's handle on the bond
  const { hold } = useObservable(tools);
  const setHold = (next: Hold) => tools.holding(next);
  const [refusal, setRefusal] = useState<string | null>(null);
  const holds: Segment<Hold>[] = [
    { value: "first", label: index.label(bond.first), title: `Keep ${index.label(bond.first)} where it is and move ${index.label(bond.second)}` },
    { value: "second", label: index.label(bond.second), title: `Keep ${index.label(bond.second)} where it is and move ${index.label(bond.first)}` },
    { value: "both", label: "Neither", title: "Move both atoms, half the change each, so the middle of the bond stays put" },
  ];
  const [chosen, setChosen] = useState<{ first?: string; second?: string }>({});

  // the atoms the torsion is measured from: the user's choice where it still stands, else the heaviest neighbour
  const near = referencesAt(index, bond, "first");
  const far = referencesAt(index, bond, "second");
  const fallback = defaultReference(index, bond);
  const reference = fallback && {
    first: chosen.first && near.includes(chosen.first) ? chosen.first : fallback.first,
    second: chosen.second && far.includes(chosen.second) ? chosen.second : fallback.second,
  };
  const inRing = sideOf(index, bond, bond.first) === null;

  // the viewport marks the torsion shown here, and stops when the pane goes
  const markedFirst = reference?.first;
  const markedSecond = reference?.second;
  useEffect(() => {
    tools.showTorsion(markedFirst && markedSecond ? { bond: bond.id, first: markedFirst, second: markedSecond } : null);
    return () => tools.showTorsion(null);
  }, [tools, bond.id, markedFirst, markedSecond]);

  // the session decides what an edit does and whether it is allowed; this only says why it was not
  const heldAtom = hold === "first" ? bond.first : hold === "second" ? bond.second : null;
  const told = (result: EditResult) => setRefusal(result.kind === "refused" ? result.message : null);

  const first = index.atoms.get(bond.first);
  const second = index.atoms.get(bond.second);
  const a = reference && index.atoms.get(reference.first);
  const d = reference && index.atoms.get(reference.second);
  const angle = first && second && a && d ? torsion(a.position, first.position, second.position, d.position) : null;
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
                onCommit={(value) => void setBondLength(bond.id, value, heldAtom).then(told)}
              />
              <span className="unit">Å</span>
            </dd>
            <dt>Hold</dt>
            <dd>
              <Segmented label="Which atom of the bond stays where it is" value={hold} options={holds} onChange={setHold} />
            </dd>
            {reference && angle !== null && (
              <>
                <dt>Torsion</dt>
                <dd className="position">
                  <NumberField
                    label={`Torsion about ${index.bondLabel(bond)}, from ${index.label(reference.first)} to ${index.label(reference.second)}, in degrees`}
                    value={angle}
                    digits={1}
                    disabled={inRing}
                    onCommit={(value) => void setBondTorsion(bond.id, reference.first, reference.second, value, heldAtom).then(told)}
                  />
                  <span className="unit">°</span>
                </dd>
                <dt>From</dt>
                <dd className="position">
                  <select
                    value={reference.first}
                    aria-label={`The atom on ${index.label(bond.first)} the torsion is measured from`}
                    onChange={(event) => setChosen({ ...chosen, first: event.target.value })}
                  >
                    {near.map((id) => (
                      <option key={id} value={id}>
                        {index.label(id)}
                      </option>
                    ))}
                  </select>
                  <span className="unit">to</span>
                  <select
                    value={reference.second}
                    aria-label={`The atom on ${index.label(bond.second)} the torsion is measured from`}
                    onChange={(event) => setChosen({ ...chosen, second: event.target.value })}
                  >
                    {far.map((id) => (
                      <option key={id} value={id}>
                        {index.label(id)}
                      </option>
                    ))}
                  </select>
                </dd>
              </>
            )}
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
    return "This bond is in a ring, so only its two atoms move and the rest of the ring is left behind. Optimise afterwards to relax it. It cannot be turned, so its torsion is read-only.";
  }
  return "Everything beyond the atom that moves goes with it, so no other length or angle changes. A negative length passes it through the held atom to the other side. The torsion turns that side about the bond.";
}
