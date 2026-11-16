import {COMMON_ELEMENTS, ELEMENTS} from "../geometry/elements";

export interface ElementPickerProps {
  /** What the picker is for. */
  label: string;
  /** The element the session holds. */
  value: string;
  disabled?: boolean;
  /** Told the element the user chose. */
  onPick: (element: string) => void;
}

/** A choice of element, with the common ones first. */
export function ElementPicker({ label, value, disabled = false, onPick }: ElementPickerProps) {
  const others = ELEMENTS.filter((symbol) => !COMMON_ELEMENTS.includes(symbol));
  const unlisted = !ELEMENTS.includes(value);
  return (
    <select aria-label={label} value={value} disabled={disabled} onChange={(event) => onPick(event.target.value)}>
      {unlisted && <option value={value}>{value}</option>}
      <optgroup label="Common">
        {COMMON_ELEMENTS.map((symbol) => (
          <option key={symbol} value={symbol}>
            {symbol}
          </option>
        ))}
      </optgroup>
      <optgroup label="All elements">
        {others.map((symbol) => (
          <option key={symbol} value={symbol}>
            {symbol}
          </option>
        ))}
      </optgroup>
    </select>
  );
}
