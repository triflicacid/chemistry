import type { Span } from "./span";

/** One unit of a fragmented formula */
export interface UnitInfo {
  span: Span;
  /** How many of the unit there are. */
  count: number;
  /** Indices into MoleculeMetadata.fragments. */
  fragments: number[];
}

export interface FragmentedInfo {
  text: string;
  units: UnitInfo[];
}

export interface FormulaInfo {
  /** e.g. C₆H₁₂O₆. */
  molecular: string;
  /** e.g. CH₂O. */
  empirical: string;
  /** e.g. CH₃CH₂OH. */
  condensed: string;
  /** e.g. Cu²⁺·SO₄²⁻·5H₂O. */
  components: FragmentedInfo;
  /** e.g. CuSO₄·5H₂O. */
  neutral_units: FragmentedInfo;
}
