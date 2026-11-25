import {Color} from "three";

/** The colour of the most negative value of a diverging scale: where electrons are plentiful. */
export const NEGATIVE_COLOUR = "#d7301f";

/** The colour of zero on a diverging scale. */
export const NEUTRAL_COLOUR = "#f4f4f4";

/** The colour of the most positive value of a diverging scale: where electrons are scarce. */
export const POSITIVE_COLOUR = "#2166d3";

/** The least range a scale is drawn over, so that values that are all but zero do not look as strong as real charges. */
export const MIN_LIMIT = 0.05;

const NEGATIVE = new Color(NEGATIVE_COLOUR);
const NEUTRAL = new Color(NEUTRAL_COLOUR);
const POSITIVE = new Color(POSITIVE_COLOUR);

/**
 * How far from zero a scale should reach to show these values.
 *
 * @param values what the scale is for
 * @returns the largest magnitude, or {@link MIN_LIMIT} if none is larger
 */
export function symmetricLimit(values: Iterable<number>): number {
  let limit = MIN_LIMIT;
  for (const value of values) {
    if (Number.isFinite(value)) {
      limit = Math.max(limit, Math.abs(value));
    }
  }
  return limit;
}

/**
 * Colours a value on a scale that is red below zero, near white at zero and blue above it.
 *
 * @param value what to colour
 * @param limit the magnitude at which the colour is as strong as it gets; larger magnitudes are drawn the same
 * @returns the colour, as a CSS hex string
 */
export function diverging(value: number, limit: number): string {
  const amount = limit > 0 && Number.isFinite(value) ? Math.max(-1, Math.min(1, value / limit)) : 0;
  const colour = NEUTRAL.clone().lerp(amount < 0 ? NEGATIVE : POSITIVE, Math.abs(amount));
  return `#${colour.getHexString()}`;
}

/**
 * @returns a CSS gradient that runs from the negative colour through the neutral one to the positive, to draw a legend with
 */
export function divergingGradient(): string {
  return `linear-gradient(to right, ${NEGATIVE_COLOUR}, ${NEUTRAL_COLOUR}, ${POSITIVE_COLOUR})`;
}
