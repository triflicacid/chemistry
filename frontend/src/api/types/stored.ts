/** The server's id for a stored molecule. */
export type MoleculeId = string;

/** A molecule the server has stored. */
export interface StoredMolecule {
  id: MoleculeId;
  smiles: string;
  canonical_smiles: string;
}
