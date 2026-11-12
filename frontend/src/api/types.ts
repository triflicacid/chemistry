/** One option as described by `GET /api/options`. */
export interface OptionDescriptor {
  name: string;
  label: string;
  description: string;
  type: "boolean" | "number" | "choice" | "arrangement";
  default_value: boolean | number | string;
  unit: string | null;
  min: number | null;
  max: number | null;
  choices: { value: string; label: string }[] | null;
  /** This option has no effect while `option` has `value`. */
  ignored_when: { option: string; value: string } | null;
}

/** Option values by name. */
export type OptionValues = Record<string, boolean | number | string>;
