/** How shiny the atoms and bonds are drawn: not at all, or like glossy plastic that reflects its surroundings. */
export type Finish = "matte" | "glossy";

/** The finishes, in the order they are offered. */
export const FINISHES: readonly Finish[] = ["matte", "glossy"];

/**
 * @param finish a finish
 * @returns how rough a surface with that finish is, from 0 for a mirror to 1 for chalk
 */
export function roughnessOf(finish: Finish): number {
  return finish === "glossy" ? 0.15 : 0.5;
}

/**
 * @param value anything, such as what was stored on an earlier visit
 * @returns it if it is a finish, and otherwise the default, which is matte
 */
export function asFinish(value: unknown): Finish {
  return FINISHES.find((finish) => finish === value) ?? "matte";
}
