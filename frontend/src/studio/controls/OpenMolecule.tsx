import { useState } from "react";

export interface OpenMoleculeProps {
  message: string;
  onOpen: (smiles: string) => void;
}

/** Asks for a molecule when the URL could not supply one. */
export function OpenMolecule({ message, onOpen }: OpenMoleculeProps) {
  const [smiles, setSmiles] = useState("");

  return (
    <form
      className="open-molecule"
      onSubmit={(event) => {
        event.preventDefault();
        const written = smiles.trim();
        if (written) {
          onOpen(written);
        }
      }}
    >
      <p>{message}</p>
      <label htmlFor="open-smiles">SMILES</label>
      <input
        id="open-smiles"
        type="text"
        className="mono"
        value={smiles}
        placeholder="OC(=O)c1ccccc1"
        autoComplete="off"
        spellCheck={false}
        onChange={(event) => setSmiles(event.target.value)}
      />
      <button type="submit" disabled={smiles.trim().length === 0}>
        Open
      </button>
    </form>
  );
}
