import type {EnergyTerms} from "../session/messages";
import {toKcal} from "./scan";

/** One part of an interaction energy: what it is called and which of the chart's colours it is drawn in, which never change. */
export interface Term {
  key: keyof EnergyTerms;
  name: string;
  slot: 1 | 2 | 3 | 4;
}

/** The parts of an interaction energy, in the order they are stacked. */
export const TERMS: readonly Term[] = [
  { key: "nuclear_nuclear", name: "Nuclear–nuclear", slot: 1 },
  { key: "nuclear_electron", name: "Nuclear–electron", slot: 2 },
  { key: "electron_electron", name: "Electron–electron", slot: 3 },
  { key: "exchange_correlation", name: "Exchange–correlation", slot: 4 },
];

/** One part of an energy, drawn from `from` to `to`. */
export interface Segment extends Term {
  /** The part's size, in kilocalories per mole, which is negative if it lowers the energy. */
  value: number;
  /** Where it starts, in kilocalories per mole from zero. */
  from: number;
  /** Where it ends. */
  to: number;
}

/** An energy stacked out from zero. */
export interface Stack {
  segments: Segment[];
  /** All the parts added together, in kilocalories per mole. */
  net: number;
  /** How far the parts that lower the energy reach below zero, which is zero if none does. */
  low: number;
  /** How far the parts that raise it reach above zero. */
  high: number;
}

/**
 * Stacks the parts of an energy out from zero: those that raise it one after another to the right, and those that lower it
 * one after another to the left, each in the order of {@link TERMS}. The parts of an interaction are large and nearly cancel,
 * so a stack that started at the first part would hide that they do.
 *
 * @param terms the parts of an interaction energy, in hartree
 * @returns the stack, in kilocalories per mole
 */
export function stack(terms: EnergyTerms): Stack {
  let low = 0;
  let high = 0;
  let net = 0;
  const segments = TERMS.map((term) => {
    const value = toKcal(terms[term.key]);
    net += value;
    if (value >= 0) {
      const segment = { ...term, value, from: high, to: high + value };
      high = segment.to;
      return segment;
    }
    const segment = { ...term, value, from: low, to: low + value };
    low = segment.to;
    return segment;
  });
  return { segments, net, low, high };
}
