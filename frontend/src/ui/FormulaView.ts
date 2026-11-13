import type { FormulaInfo, UnitInfo } from "../api/types";
import { SmilesText } from "./SmilesText";

/** One formula shown in the card. */
interface Row {
  label: string;
  text: string;
  units: UnitInfo[] | null;
}

/** One formula as shown, with whatever of it can be highlighted. */
interface Shown {
  text: SmilesText;
  units: UnitInfo[];
}

export class FormulaView {
  readonly element = document.createElement("div");
  private readonly fragmented: Shown[] = [];

  constructor(formula: FormulaInfo, fragmentCount: number) {
    this.element.className = "formulae";

    for (const row of FormulaView.rows(formula, fragmentCount)) {
      const text = new SmilesText(row.text, "formula-text");
      const label = document.createElement("span");
      label.className = "formula-label";
      label.textContent = row.label;

      const line = document.createElement("span");
      line.className = row.units === null ? "formula" : "formula wide";
      line.append(label, text.element);
      this.element.append(line);

      if (row.units !== null) {
        this.fragmented.push({ text, units: row.units });
      }
    }
  }

  highlightFragment(fragment: number | null): void {
    for (const shown of this.fragmented) {
      const unit = fragment === null ? undefined : shown.units.find((candidate) => candidate.fragments.includes(fragment));
      shown.text.highlight(unit === undefined ? [] : [{ span: unit.span, colour: 0 }]);
    }
  }

  /** Chooses the formulae worth showing */
  private static rows(formula: FormulaInfo, fragmentCount: number): Row[] {
    const rows: Row[] = [];
    const add = (label: string, text: string, units: UnitInfo[] | null = null): void => {
      if (text.length > 0 && !rows.some((row) => row.text === text)) {
        rows.push({ label, text, units });
      }
    };

    add("Molecular", formula.molecular);
    add("Empirical", formula.empirical);

    // a condensed formula of a ring falls back to a ring-closure marker, which is notation for SMILES and not for a formula
    if (fragmentCount === 1 && !formula.condensed.includes("%")) {
      add("Condensed", formula.condensed);
    }
    if (fragmentCount > 1) {
      add("Units", formula.neutral_units.text, formula.neutral_units.units);
      add("Components", formula.components.text, formula.components.units);
    }
    return rows;
  }
}
