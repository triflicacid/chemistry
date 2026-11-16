import {useEffect, useMemo, useState} from "react";
import {ChemistryApi} from "../../api/ChemistryApi";
import type {MoleculeMetadata} from "../../api/types/metadata";
import type {SessionState} from "../session/messages";
import {Chemistry} from "./Chemistry";

const api = new ChemistryApi();

export interface ChemistryStatus {
  chemistry: Chemistry | null;
  failed: boolean;
}

export const NO_CHEMISTRY: ChemistryStatus = { chemistry: null, failed: false };

async function metadataOf(state: SessionState): Promise<MoleculeMetadata> {
  for (const id of [state.saved_molecule_id, state.source_molecule_id]) {
    if (id) {
      try {
        const known = await api.metadata(id);
        if (known.smiles === state.smiles) {
          return known;
        }
      } catch {
        // fall through to making one
      }
    }
  }
  const created = await api.createMolecule(state.smiles);
  return api.metadata(created.id);
}

/**
 * Reads the chemistry of the session's molecule from the server.
 *
 * @param state the session's state, or null before one opens
 * @returns the chemistry, null until it has been read, and whether reading failed
 */
export function useChemistry(state: SessionState | null): ChemistryStatus {
  const [found, setFound] = useState<{ key: string; metadata: MoleculeMetadata } | null>(null);
  const [failedKey, setFailedKey] = useState<string | null>(null);
  const key = state ? `${state.smiles}|${state.saved_molecule_id ?? ""}|${state.source_molecule_id ?? ""}` : null;

  useEffect(() => {
    if (!state || key === null) {
      return;
    }
    let current = true;
    metadataOf(state)
      .then((metadata) => current && setFound({ key, metadata }))
      .catch((error) => {
        console.warn("Could not load the molecule's metadata", error);
        if (current) {
          setFailedKey(key);
        }
      });
    return () => {
      current = false;
    };
    // the structure is what is read, so only a change of it reads again
  }, [key]);

  return useMemo(() => {
    if (!state || key === null) {
      return NO_CHEMISTRY;
    }
    if (found?.key === key) {
      return { chemistry: new Chemistry(found.metadata, state), failed: false };
    }
    return { chemistry: null, failed: failedKey === key };
  }, [state, key, found, failedKey]);
}
