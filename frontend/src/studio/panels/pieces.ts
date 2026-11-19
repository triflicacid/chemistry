/**
 * Finds where one molecule's own text begins in the text of the whole, where molecules are written between dots.
 *
 * @param whole the whole's text
 * @param own the molecule's text, or null if it has none
 * @param earlier the text of each molecule before it, which says how many identical pieces to skip
 * @returns where it begins, or null if the whole does not hold it, as it does not once an edit has changed what was read
 */
export function startOf(whole: string, own: string | null, earlier: readonly (string | null)[]): number | null {
  if (own === null) {
    return null;
  }

  let skip = earlier.filter((text) => text === own).length;
  let at = 0;
  for (const piece of whole.split(".")) {
    if (piece === own) {
      if (skip === 0) {
        return at;
      }
      skip--;
    }
    at += piece.length + 1;
  }
  return null;
}
