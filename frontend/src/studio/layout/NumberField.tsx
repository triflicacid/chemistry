import { type KeyboardEvent, useRef, useState } from "react";

export interface NumberFieldProps {
  /** What the field is for. */
  label: string;
  /** The number the session holds. */
  value: number;
  /** Places to show after the point. */
  digits?: number;
  disabled?: boolean;
  /** Told the number the user entered, on Enter or when the field loses focus. */
  onCommit: (value: number) => void;
}

/** A text field for one number. */
export function NumberField({ label, value, digits = 3, disabled = false, onCommit }: NumberFieldProps) {
  // null means the field is not focused, so it shows whatever it is given
  const [draft, setDraft] = useState<string | null>(null);
  const [opened, setOpened] = useState(value);
  const abandoned = useRef(false);

  const format = (number: number) => number.toFixed(digits);
  const entered = draft === null ? NaN : Number(draft.trim());
  const valid = draft === null || (draft.trim() !== "" && Number.isFinite(entered));
  const underneath = draft !== null && format(value) !== format(opened);

  const finish = () => {
    if (!abandoned.current && draft !== null && valid && draft.trim() !== format(opened)) {
      onCommit(entered);
    }
    abandoned.current = false;
    setDraft(null);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.currentTarget.blur();
    } else if (event.key === "Escape") {
      // leaving the field commits, unless it is told the entry is abandoned
      abandoned.current = true;
      event.currentTarget.blur();
    }
  };

  return (
    <input
      className="number-field mono"
      type="text"
      inputMode="decimal"
      spellCheck={false}
      autoComplete="off"
      aria-label={label}
      aria-invalid={!valid}
      disabled={disabled}
      title={underneath ? `Now ${format(value)} in the session; leaving the field keeps what you typed` : undefined}
      data-stale={underneath || undefined}
      value={draft ?? format(value)}
      onFocus={(event) => {
        setOpened(value);
        setDraft(format(value));
        event.currentTarget.select();
      }}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={finish}
      onKeyDown={onKeyDown}
    />
  );
}
