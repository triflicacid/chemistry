/** The colour of an orbital that is all sigma. */
export const SIGMA_COLOUR = "#1baf7a";

/** The colour of an orbital that is all pi. */
export const PI_COLOUR = "#eb6834";

/** The colour of an orbital that is neither, because the atoms are not flat enough for it to be one or the other. */
export const NEUTRAL_COLOUR = "#8a8f98";

/**
 * @param hex a colour as `#rrggbb`
 * @returns its red, green and blue, each from 0 to 255
 */
function channels(hex: string): [number, number, number] {
  return [1, 3, 5].map((at) => parseInt(hex.slice(at, at + 2), 16)) as [number, number, number];
}

/**
 * The colour an orbital is drawn in when it is coloured by its character.
 *
 * @param pi how much of the orbital is pi, from zero to one, or null if the atoms are not flat enough for it to be sigma or pi
 * @returns the colour, as `#rrggbb`
 */
export function characterColour(pi: number | null): string {
  if (pi === null) {
    return NEUTRAL_COLOUR;
  }

  const share = Math.min(1, Math.max(0, pi));
  const [from, to] = [channels(SIGMA_COLOUR), channels(PI_COLOUR)];
  return `#${from.map((channel, i) => Math.round(channel + ((to[i] as number) - channel) * share).toString(16).padStart(2, "0")).join("")}`;
}

/** The colour of the shell of an orbital that holds half of it, where the electron is most likely to be. */
export const CORE_SHELL = "#d9480f";

/** The colour of the shell that holds the share asked for. */
export const OUTER_SHELL = "#fab005";

/** The colours that tell orbitals apart when several are merged into one surface, which are as many as can be drawn at once. */
export const ORBITAL_HUES: readonly string[] = ["#2a78d6", "#eb6834", "#1baf7a", "#a545c8", "#d6a100", "#17a2b8", "#d6336c", "#5c7c2f", "#7048e8", "#b3541e", "#0b7285", "#868e96"];

/**
 * @param index the place of an orbital among those drawn
 * @returns its colour when they are merged, as `#rrggbb`
 */
export function orbitalHue(index: number): string {
  return ORBITAL_HUES[index % ORBITAL_HUES.length] as string;
}

/** The colour of the part of an orbital that is positive. */
export const POSITIVE_LOBE = "#2f6fe4";

/** The colour of the part that is negative. */
export const NEGATIVE_LOBE = "#e0453a";
