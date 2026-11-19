import {useMemo} from "react";
import type {Models} from "../models/models";
import {RingSmiles} from "./RingSmiles";

interface RingMembershipsProps {
  kind: "atom" | "bond";
  id: string;
  /** The atoms to keep marked in each ring's SMILES. */
  atoms: readonly string[];
  models: Models;
}

/** The rings an atom or bond is part of. */
export function RingMemberships({ kind, id, atoms, models }: RingMembershipsProps) {
  const { chemistry, failed } = models.chemistry;
  const rings = chemistry?.ringsOf(kind, id) ?? [];
  // an atom is marked in the first colour; the ends of a bond in the two after the one that marks the bond itself
  const marked = useMemo(
    () => atoms.map((atom, position) => ({ id: atom, colour: atoms.length === 1 ? 0 : position + 1 })),
    [atoms],
  );

  return (
    <>
      <h4 className="pane-subtitle">Ring memberships</h4>
      {rings.length === 0 ? (
        <p className="panel-empty">{chemistry ? "Not in a ring." : failed ? "Rings could not be loaded." : "Reading the rings…"}</p>
      ) : (
        <ul className="ring-list">
          {rings.map((ring) => (
            <li key={ring.id} title={`Ring ${ring.id.slice(1)}, ${ring.info.size} atoms. Click to show it in the Outliner`}>
              <RingSmiles ring={ring} models={models} marked={marked} onPick={() => models.reveal.reveal({ kind: "ring", id: ring.id })} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
