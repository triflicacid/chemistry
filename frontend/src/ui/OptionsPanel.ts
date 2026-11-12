import type { OptionDescriptor, OptionValues } from "../api/types";

interface Control {
  element: HTMLElement;
  /** The value now, or undefined if it is the default (or invalid/blank). */
  read(): boolean | number | string | undefined;
  reset(): void;
  setDisabled(disabled: boolean): void;
}

export class OptionsPanel {
  readonly element = document.createElement("section");
  readonly toggle: HTMLButtonElement;
  private readonly controls = new Map<string, { descriptor: OptionDescriptor; control: Control }>();

  constructor(descriptors: OptionDescriptor[]) {
    this.element.className = "options";

    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "small";
    toggle.textContent = "Options";
    this.toggle = toggle;
    toggle.setAttribute("aria-expanded", "false");

    const body = document.createElement("div");
    body.className = "options-body";
    body.hidden = true;

    toggle.addEventListener("click", () => {
      body.hidden = !body.hidden;
      toggle.setAttribute("aria-expanded", String(!body.hidden));
    });

    const reset = document.createElement("button");
    reset.type = "button";
    reset.textContent = "Reset to defaults";
    reset.addEventListener("click", () => this.reset());

    for (const descriptor of descriptors) {
      const control = OptionsPanel.build(descriptor, () => this.refresh());
      this.controls.set(descriptor.name, { descriptor, control });
      body.append(OptionsPanel.row(descriptor, control.element));
    }
    body.append(reset);

    this.element.append(body);
    this.refresh();
  }

  /** Only the options the user changed from their defaults. */
  values(): OptionValues {
    const values: OptionValues = {};
    for (const [name, { control }] of this.controls) {
      const value = control.read();
      if (value !== undefined) {
        values[name] = value;
      }
    }
    return values;
  }

  private reset(): void {
    this.controls.forEach(({ control }) => control.reset());
    this.refresh();
  }

  /** Disables options that have no effect given the others. */
  private refresh(): void {
    for (const { descriptor, control } of this.controls.values()) {
      const rule = descriptor.ignored_when;
      const other = rule ? this.controls.get(rule.option) : undefined;
      const current = other ? (other.control.read() ?? other.descriptor.default_value) : undefined;
      control.setDisabled(rule !== null && current === rule.value);
    }
  }

  private static row(descriptor: OptionDescriptor, control: HTMLElement): HTMLElement {
    const row = document.createElement("div");
    row.className = "option";
    row.title = descriptor.description;

    const label = document.createElement("span");
    label.className = "option-label";
    label.textContent = descriptor.unit && descriptor.type === "number" ? `${descriptor.label} (${descriptor.unit})` : descriptor.label;

    row.append(label, control);
    return row;
  }

  private static build(descriptor: OptionDescriptor, onChange: () => void): Control {
    switch (descriptor.type) {
      case "boolean":
        return OptionsPanel.checkbox(descriptor, onChange);
      case "number":
        return OptionsPanel.number(descriptor, onChange);
      case "choice":
        return OptionsPanel.radios(descriptor, onChange);
      case "arrangement":
        return OptionsPanel.arrangement(descriptor, onChange);
    }
  }

  private static checkbox(descriptor: OptionDescriptor, onChange: () => void): Control {
    const input = document.createElement("input");
    input.type = "checkbox";
    input.checked = descriptor.default_value === true;
    input.addEventListener("change", onChange);

    return {
      element: input,
      read: () => (input.checked === (descriptor.default_value === true) ? undefined : input.checked),
      reset: () => (input.checked = descriptor.default_value === true),
      setDisabled: (disabled) => (input.disabled = disabled),
    };
  }

  private static number(descriptor: OptionDescriptor, onChange: () => void): Control {
    const input = document.createElement("input");
    input.type = "number";
    input.value = String(descriptor.default_value);
    if (descriptor.min !== null) input.min = String(descriptor.min);
    if (descriptor.max !== null) input.max = String(descriptor.max);
    input.step = "any";
    input.addEventListener("input", onChange);

    return {
      element: input,
      read: () => {
        const value = input.valueAsNumber;
        return Number.isNaN(value) || value === descriptor.default_value ? undefined : value;
      },
      reset: () => (input.value = String(descriptor.default_value)),
      setDisabled: (disabled) => (input.disabled = disabled),
    };
  }

  private static radioGroup(descriptor: OptionDescriptor, onChange: () => void) {
    const group = document.createElement("div");
    group.className = "radios";
    group.setAttribute("role", "radiogroup");

    const inputs = (descriptor.choices ?? []).map((choice) => {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = "radio";
      input.name = `option-${descriptor.name}`;
      input.value = choice.value;
      input.addEventListener("change", onChange);
      label.append(input, ` ${choice.label}`);
      group.append(label);
      return input;
    });

    const select = (value: string) => inputs.forEach((input) => (input.checked = input.value === value));
    const selected = () => inputs.find((input) => input.checked)?.value ?? String(descriptor.default_value);
    return { group, inputs, select, selected };
  }

  private static radios(descriptor: OptionDescriptor, onChange: () => void): Control {
    const { group, inputs, select, selected } = OptionsPanel.radioGroup(descriptor, onChange);
    select(String(descriptor.default_value));

    return {
      element: group,
      read: () => (selected() === descriptor.default_value ? undefined : selected()),
      reset: () => select(String(descriptor.default_value)),
      setDisabled: (disabled) => inputs.forEach((input) => (input.disabled = disabled)),
    };
  }

  /** Radios for the line direction plus an optional "per line" count, sent as `row` or `row:3`. */
  private static arrangement(descriptor: OptionDescriptor, onChange: () => void): Control {
    const { group, inputs, select, selected } = OptionsPanel.radioGroup(descriptor, onChange);
    select(String(descriptor.default_value));

    const count = document.createElement("input");
    count.type = "number";
    count.min = String(descriptor.min ?? 1);
    count.step = "1";
    count.placeholder = "all";
    count.title = descriptor.unit ?? "";
    count.addEventListener("input", onChange);

    const element = document.createElement("div");
    element.className = "arrangement";
    element.append(group, count);

    return {
      element,
      read: () => {
        const lines = selected();
        const perLine = count.valueAsNumber;
        const text = Number.isInteger(perLine) ? `${lines}:${perLine}` : lines;
        return text === descriptor.default_value ? undefined : text;
      },
      reset: () => {
        select(String(descriptor.default_value));
        count.value = "";
      },
      setDisabled: (disabled) => {
        inputs.forEach((input) => (input.disabled = disabled));
        count.disabled = disabled;
      },
    };
  }
}
