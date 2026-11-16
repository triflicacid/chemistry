import {useEffect} from "react";
import {electronsOf, notationOf} from "../../notation/ringText";
import type {StudioRing} from "../chemistry/Chemistry";
import type {StateIndex} from "../geometry/StateIndex";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import {Links, Property} from "./paneParts";
import {RingSmiles} from "./RingSmiles";

interface RingPaneProps {
  ring: StudioRing;
  index: StateIndex;
  models: Models;
}

/** Describe a ring. */
export function RingPane({ ring, index, models }: RingPaneProps) {
  const { tools, chemistry } = models;
  const shown = useObservable(tools).system.clouds.includes(ring.id);
  const { system } = ring;
  const fused = chemistry.chemistry?.fusedWith(ring) ?? [];

  // the label is drawn while the pane is up; the cloud is the checkbox's, and outlasts the pane
  useEffect(() => {
    tools.showSystem(ring.id);
    return () => tools.showSystem(null);
  }, [tools, ring.id]);

  return (
    <>
      <h3 className="pane-title">Ring {ring.id.slice(1)} ({ring.info.size} atoms)</h3>
      <dl className="properties">
        <Property name="ID" value={ring.id} mono />
      </dl>

      <h4 className="pane-subtitle">Chemistry</h4>
      <dl className="properties">
        <Property name="Formula" value={ring.info.formula} />
        <dt>SMILES</dt>
        <dd className="wrap mono">
          <RingSmiles ring={ring} models={models} onPick={() => models.focus.focus(ring.atoms)} />
        </dd>
        <Property name="Written" value={notationOf(ring.info)} />
        {system ? (
          <>
            <Property name="Aromaticity" value={system.aromaticity} />
            <Property name="π electrons" value={electronsOf(system)} />
          </>
        ) : (
          <Property name="Aromaticity" value="none (its atoms cannot all join a π system)" />
        )}
        {system && (
          <>
            <dt>π system</dt>
            <dd>
              <label className="check">
                <input
                  type="checkbox"
                  checked={shown}
                  onChange={(event) => tools.showCloud(ring.id, event.target.checked)}
                />
                Show π system
              </label>
            </dd>
          </>
        )}
      </dl>

      {fused.length > 0 && (
        <>
          <h4 className="pane-subtitle">Fused with</h4>
          <Links models={models} links={fused.map((other) => ({ item: { kind: "ring", id: other.id }, label: `Ring ${other.id.slice(1)}: ${other.info.smiles}` }))} />
        </>
      )}

      <h4 className="pane-subtitle">Atoms</h4>
      <Links models={models} links={ring.atoms.map((id) => ({ item: { kind: "atom", id }, label: index.label(id) }))} />

      <h4 className="pane-subtitle">Bonds</h4>
      <Links
        models={models}
        links={ring.bonds.flatMap((id) => {
          const bond = index.bonds.get(id);
          return bond ? [{ item: { kind: "bond" as const, id }, label: index.bondLabel(bond) }] : [];
        })}
      />
    </>
  );
}
