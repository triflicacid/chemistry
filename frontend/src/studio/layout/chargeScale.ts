import {diverging, symmetricLimit} from "../../render/scale";
import type {ChargesResult} from "../session/messages";

/** Partial charges drawn as colours. */
export interface ChargeScale {
  /** The colour of each atom that has a charge, by the atom's identifier. */
  colours: Map<string, string>;
  /** The charge of each atom, in elementary charges, by the atom's identifier. */
  charges: Map<string, number>;
  /** The charge at which the colour is as strong as it gets, in elementary charges. */
  limit: number;
}

/**
 * Colours atoms by their partial charge on a scale centred on zero.
 *
 * @param result what the server last said of the charges
 * @returns the colours and the range they cover, or null if there are no charges to show
 */
export function chargeScale(result: ChargesResult | null): ChargeScale | null {
  if (!result || result.status !== "complete" || result.charges.length === 0) {
    return null;
  }

  const limit = symmetricLimit(result.charges.map(({ charge }) => charge));
  return {
    colours: new Map(result.charges.map(({ id, charge }) => [id, diverging(charge, limit)])),
    charges: new Map(result.charges.map(({ id, charge }) => [id, charge])),
    limit,
  };
}
