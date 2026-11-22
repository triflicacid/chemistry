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
