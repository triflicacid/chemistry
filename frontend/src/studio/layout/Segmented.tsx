import {type KeyboardEvent, useRef} from "react";

/** One choice in a {@link Segmented} control. */
export interface Segment<T extends string> {
  value: T;
  label: string;
  /** What hovering it says, shown as a tooltip. */
  title?: string;
  disabled?: boolean;
}

export interface SegmentedProps<T extends string> {
  /** What the choices are a choice of, for anyone who cannot see the control. */
  label: string;
  /** The choice that is selected. Exactly one is. */
  value: T;
  options: readonly Segment<T>[];
  onChange: (value: T) => void;
}

/** A radio group inside a button-like container. */
export function Segmented<T extends string>({ label, value, options, onChange }: SegmentedProps<T>) {
  const group = useRef<HTMLDivElement | null>(null);

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const step = event.key === "ArrowRight" || event.key === "ArrowDown" ? 1 : event.key === "ArrowLeft" || event.key === "ArrowUp" ? -1 : 0;
    if (step === 0) {
      return;
    }

    // the next choice that can be chosen, going round
    const here = options.findIndex((option) => option.value === value);
    for (let offset = 1; offset <= options.length; offset++) {
      const next = options[(here + step * offset + options.length * offset) % options.length];
      if (next && !next.disabled) {
        event.preventDefault();
        onChange(next.value);
        group.current?.querySelector<HTMLButtonElement>(`[data-value="${CSS.escape(next.value)}"]`)?.focus();
        return;
      }
    }
  };

  return (
    <div ref={group} className="segmented" role="radiogroup" aria-label={label} onKeyDown={onKeyDown}>
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            className="segment"
            data-value={option.value}
            aria-checked={selected}
            // only the selected section is in the tab order, as for radio buttons
            tabIndex={selected ? 0 : -1}
            disabled={option.disabled}
            title={option.title}
            onClick={() => onChange(option.value)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
