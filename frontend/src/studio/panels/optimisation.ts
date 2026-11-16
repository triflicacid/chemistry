import type {OptimisationOutcome} from "../session/useSession";

/** How an optimisation should read: it helped, it did nothing worth noting, or it did not go well. */
export type Tone = "good" | "neutral" | "bad";

export interface Described {
  tone: Tone;
  /** One line saying what happened. */
  headline: string;
  /** Smaller print under it, or null if there is nothing more to say. */
  detail: string | null;
}

/**
 * Says in words how an optimisation went. The strains are the toolbar's own, so the improvement can be checked there.
 *
 * @param outcome how the optimisation ended
 */
export function describeOutcome(outcome: OptimisationOutcome): Described {
  const { status, strainBefore: before, strainAfter: after } = outcome;

  if (status === "failed") {
    return { tone: "bad", headline: "Optimisation failed", detail: outcome.message };
  }
  if (status === "cancelled") {
    return { tone: "neutral", headline: "Optimisation stopped", detail: outcome.message ? `${outcome.message}. The structure is as it was` : "The structure is as it was" };
  }
  if (status === "unchanged") {
    return {
      tone: "neutral",
      headline: "Already as relaxed as it can be",
      detail: before !== null ? `Strain ${before.toFixed(1)}. Nothing was moved` : "Nothing was moved",
    };
  }

  const moved = `${outcome.atoms} ${outcome.atoms === 1 ? "atom" : "atoms"} moved in ${outcome.iterations} steps (undoable in one)`;
  if (before === null || after === null) {
    return { tone: "neutral", headline: "Optimised", detail: moved };
  }

  const range = `${before.toFixed(1)} → ${after.toFixed(1)}`;
  if (after < before) {
    const share = before > 0 ? ` (${Math.round(((before - after) / before) * 100)}% lower)` : "";
    return { tone: "good", headline: `Optimised: strain ${range}${share}`, detail: moved };
  }

  // the optimiser lowered what it minimises, which is not exactly what the toolbar measures
  return {
    tone: "neutral",
    headline: `Optimised, but strain did not improve: ${range}`,
    detail: `${moved}. The optimiser weighs stereochemistry and flatness as well as the bond lengths, angles and contacts that strain counts`,
  };
}
