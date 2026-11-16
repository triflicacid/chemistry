import "./notation.css";
import type { FormulaInfo, UnitInfo } from "../api/types/formula";
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

/** What reacts to the pointer on a fragmented formula, each told the fragments the unit under it stands for. */
export interface FormulaHandlers {
  /** The pointer is over a unit, or null when it is over none. */
  hover: (fragments: readonly number[] | null) => void;
  pick: (fragments: readonly number[]) => void;
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

  /**
   * Makes the units of the fragmented formulae respond to the pointer.
   *
   * @param handlers the handler called on hover and click
   * @returns how to stop listening
   */
  listen(handlers: FormulaHandlers): () => void {
    const stops = this.fragmented.map((shown) => {
      const unitAt = (offset: number) => shown.units.find((unit) => offset >= unit.span.start && offset < unit.span.end);
      return shown.text.listen({
        hover: (offset) => handlers.hover((offset === null ? undefined : unitAt(offset))?.fragments ?? null),
        pick: (offset) => {
          const unit = unitAt(offset);
          if (unit) {
            handlers.pick(unit.fragments);
          }
        },
      });
    });
    return () => stops.forEach((stop) => stop());
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
