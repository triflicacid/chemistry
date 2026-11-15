/** A molecule of the server's library. */
export interface LibraryEntry {
  name: string;
  smiles: string;
  classes: string[];
}

/** A SMARTS pattern of the library, with the words that explain it. */
export interface QueryEntry {
  name: string;
  smarts: string;
  classes: string[];
  description: string;
  /** A molecule the pattern matches, or blank if the library gives none. */
  example: string;
}
