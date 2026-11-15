/** What the URL asks Studio to open. */
export interface Requested {
  /** A molecule the server has stored, which may no longer exist. */
  moleculeId?: string;
  /** The molecule written out, which survives a server restart as an ID does not. */
  smiles?: string;
}

/** Reads what to open from the query string. */
export function requested(search = window.location.search): Requested {
  const parameters = new URLSearchParams(search);
  const moleculeId = parameters.get("molecule")?.trim();
  const smiles = parameters.get("smiles")?.trim();

  return {
    moleculeId: moleculeId || undefined,
    smiles: smiles || undefined,
  };
}

/** Rewrites the query string to name both the molecule and its SMILES. */
export function describing(moleculeId: string, smiles: string): void {
  const url = new URL(window.location.href);
  url.searchParams.set("molecule", moleculeId);
  url.searchParams.set("smiles", smiles);
  window.history.replaceState(null, "", url);
}
