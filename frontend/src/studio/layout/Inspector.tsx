import type { SessionState } from "../session/messages";

export interface InspectorProps {
  state: SessionState | null;
}

/** The properties of whatever is selected. */
export function Inspector({ state }: InspectorProps) {
  if (!state) {
    return <p className="panel-empty">Nothing is open.</p>;
  }

  return (
    <dl className="properties">
      <Property name="SMILES" value={state.smiles} mono />
      <Property name="Atoms" value={String(state.atoms.length)} />
      <Property name="Bonds" value={String(state.bonds.length)} />
      <Property name="Fragments" value={String(state.fragments.length)} />
      <Property name="Strain" value={state.strain.toFixed(3)} />
      <Property name="Version" value={String(state.version)} />
      <Property name="Source" value={state.source_molecule_id} mono />
      <Property name="Saved" value={state.saved_molecule_id ?? "not saved"} mono={state.saved_molecule_id !== null} />
    </dl>
  );
}

interface PropertyProps {
  name: string;
  value: string;
  mono?: boolean;
}

function Property({ name, value, mono = false }: PropertyProps) {
  return (
    <>
      <dt>{name}</dt>
      <dd className={mono ? "mono" : undefined} title={value}>
        {value}
      </dd>
    </>
  );
}
