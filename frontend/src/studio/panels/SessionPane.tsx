import type {Models} from "../models/models";
import type {GeometryResult, SessionState} from "../session/messages";
import {describeStrain} from "../layout/findings";
import {MoleculeDetails} from "./MoleculeDetails";
import {Property} from "./paneParts";

/** Overview of the session, in the Inspect pane when nothing is selected. */
export function SessionPane({ state, geometry, models }: { state: SessionState; geometry: GeometryResult | null; models: Models }) {
  return (
    <>
      <h3 className="pane-title">Molecule</h3>
      <MoleculeDetails state={state} models={models}>
        <Property name="Atoms" value={String(state.atoms.length)} />
        <Property name="Bonds" value={String(state.bonds.length)} />
        <Property name="Molecules" value={String(state.fragments.length)} />
        <Property name="Strain" value={describeStrain(state, geometry)} />
      </MoleculeDetails>
      <hr className="pane-divider" />
      <h3 className="pane-title">Session</h3>
      <dl className="properties">
        <Property name="Version" value={String(state.version)} />
        <Property name="Source" value={state.source_molecule_id} mono />
        <Property name="Saved" value={state.saved_molecule_id ?? "not saved"} mono={state.saved_molecule_id !== null} />
      </dl>
    </>
  );
}
