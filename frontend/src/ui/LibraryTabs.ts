import type { ChemistryApi } from "../api/ChemistryApi";
import { libraryAction, libraryCell, libraryRow, type LibraryTab } from "./LibraryDialog";

/**
 * The molecules to draw.
 *
 * @param api the server to search
 * @param insert shows a molecule in a panel of its own
 */
export function moleculeTab(api: ChemistryApi, insert: (smiles: string) => Promise<void> | void): LibraryTab {
  return {
    label: "Molecules",
    placeholder: "Search by name",
    nouns: ["molecule", "molecules"],
    headings: ["Name", "SMILES", "Categories"],
    categories: () => api.moleculeCategories(),
    rows: async (name, categories) => {
      const found = await api.searchMolecules(name, categories);
      return found.map((entry) =>
        libraryRow(
          [entry.name, libraryCell(entry.smiles, "library-smiles"), libraryCell(entry.classes.join(", "), "library-classes")],
          [libraryAction("Insert", `Draw ${entry.name}`, () => insert(entry.smiles))],
        ),
      );
    },
  };
}

/**
 * The SMARTS patterns to search molecules with.
 *
 * @param api the server to search
 * @param apply searches the molecules on the page for a pattern
 * @param insert shows a molecule in a panel of its own
 */
export function queryTab(
  api: ChemistryApi,
  apply: (smarts: string) => void,
  insert: (smiles: string) => Promise<void> | void,
): LibraryTab {
  return {
    label: "Queries",
    placeholder: "Search by name",
    nouns: ["pattern", "patterns"],
    headings: ["Name", "SMARTS", "What it matches", "Categories"],
    categories: () => api.queryCategories(),
    rows: async (name, categories) => {
      const found = await api.searchQueries(name, categories);
      return found.map((entry) => {
        const actions = [libraryAction("Search", `Look for ${entry.name} in the molecules shown`, () => apply(entry.smarts))];
        if (entry.example) {
          actions.push(
            libraryAction("Try", `Draw ${entry.example} and look for ${entry.name} in it`, async () => {
              await insert(entry.example);
              apply(entry.smarts);
            }),
          );
        }
        return libraryRow(
          [
            entry.name,
            libraryCell(entry.smarts, "library-smiles"),
            libraryCell(entry.description, "library-description"),
            libraryCell(entry.classes.join(", "), "library-classes"),
          ],
          actions,
        );
      });
    },
  };
}
