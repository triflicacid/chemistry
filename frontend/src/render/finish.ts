/** How shiny the atoms and bonds are drawn. */
export type Finish = "matte" | "glossy";

export const FINISHES: readonly Finish[] = ["matte", "glossy"];

/**
 * @param finish a finish
 * @returns how rough a surface with that finish is, from 0 for a mirror to 1 for chalk
 */
export function roughnessOf(finish: Finish): number {
  return finish === "glossy" ? 0.15 : 0.5;
}

/**
 * @param value anything
 * @returns it if it is a finish, and otherwise the default
 */
export function asFinish(value: unknown): Finish {
  return FINISHES.find((finish) => finish === value) ?? "matte";
}
