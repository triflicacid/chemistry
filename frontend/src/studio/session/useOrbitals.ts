import {useCallback, useEffect, useState} from "react";
import type {FieldResult, InteractionResult, OrbitalsResult, OrbitalTarget} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";
import {useWarner} from "./warnings";

/** How many fields are kept. */
const KEPT = 160;

export interface Orbitals {
  /** The orbital energy levels as the server last said, or null if it has not. They describe the version in `version`. */
  orbitals: OrbitalsResult | null;
  /** When the calculation of the levels under way was asked for, as {@link Date.now} says, or null if there is none. */
  orbitalsSince: number | null;
  /**
   * @param model the kind of Hartree-Fock calculation
   * @param fragments the molecules to calculate each on its own, as an atom of each; empty for the whole session as one
   * @param spins the spin multiplicity to calculate each at, by an atom of the molecule, with the whole session under the empty string
   */
  requestOrbitals: (model: string, fragments: readonly string[], spins: Readonly<Record<string, number>>) => void;
  /** The values of orbitals on grids as the server last said, one for each orbital asked for, which may be several at once. */
  fields: readonly FieldResult[];
  /** Whether orbitals have been asked for and have not yet arrived. */
  fieldsAsked: boolean;
  /**
   * @param model the kind of Hartree-Fock calculation
   * @param targets the orbitals to evaluate
   * @param spins the spin multiplicity to calculate each molecule at, as for {@link requestOrbitals}
   */
  requestOrbital: (model: string, targets: readonly OrbitalTarget[], spins: Readonly<Record<string, number>>) => void;
  /** How the orbitals of everything together are made of those of each molecule on its own, as the server last said, or null if it has not. */
  interaction: InteractionResult | null;
  /** When the calculation of it under way was asked for, as {@link Date.now} says, or null if there is none. */
  interactionSince: number | null;
  /**
   * Asks the server for how the orbitals of everything together are made of those of each molecule.
   *
   * @param model the kind of Hartree-Fock calculation
   * @param spins the spin multiplicity to calculate each at, as for {@link requestOrbitals}
   */
  requestInteraction: (model: string, spins: Readonly<Record<string, number>>) => void;
}

/**
 * @param first a field
 * @param second another
 * @returns whether they are of the same orbital of the same molecule of the same session
 */
function sameField(first: FieldResult, second: FieldResult): boolean {
  return first.session_id === second.session_id && first.fragment === second.fragment && first.spin === second.spin && first.orbital === second.orbital;
}

/**
 * Keeps the molecular orbitals the server sends, and asks for them.
 *
 * @param link the connection
 */
export function useOrbitals({ socketRef, held, note, warn }: SessionLink): Orbitals {
  const warnLevels = useWarner(warn);
  const warnField = useWarner(warn);
  const warnInteraction = useWarner(warn);
  const [orbitals, setOrbitals] = useState<OrbitalsResult | null>(null);
  const [orbitalsSince, setOrbitalsSince] = useState<number | null>(null);
  const [fields, setFields] = useState<readonly FieldResult[]>([]);
  const [fieldsAsked, setFieldsAsked] = useState(false);
  const [interaction, setInteraction] = useState<InteractionResult | null>(null);
  const [interactionSince, setInteractionSince] = useState<number | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    const hearLevels = socket.on<OrbitalsResult>("orbitals.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setOrbitalsSince(null);
      warnLevels(result.status === "complete" ? null : (result.message ?? "The orbitals could not be calculated."));
      setOrbitals(result);
    });

    const hearField = socket.on<FieldResult>("field.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setFieldsAsked(false);
      warnField(result.status === "complete" ? null : (result.message ?? "An orbital could not be worked out."));
      // a field of an older version is no use beside one of a newer, and one of the same orbital is replaced by this
      setFields((before) =>
        [...before.filter((each) => each.session_id === result.session_id && each.version === result.version && !sameField(each, result)), result].slice(-KEPT),
      );
    });

    const hearInteraction = socket.on<InteractionResult>("interaction.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setInteractionSince(null);
      warnInteraction(result.status === "complete" ? null : (result.message ?? "The diagram could not be made."));
      setInteraction(result);
    });

    return () => {
      hearLevels();
      hearField();
      hearInteraction();
    };
  }, [held, socketRef, warnLevels, warnField, warnInteraction]);

  const requestOrbitals = useCallback(
    (model: string, fragments: readonly string[], spins: Readonly<Record<string, number>>) => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setOrbitalsSince(Date.now());
      socket.requestOrbitals(base.session_id, model, fragments, spins).catch((cause: unknown) => {
        setOrbitalsSince(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  const requestOrbital = useCallback(
    (model: string, targets: readonly OrbitalTarget[], spins: Readonly<Record<string, number>>) => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base || targets.length === 0) {
        return;
      }

      setFieldsAsked(true);
      socket.requestOrbital(base.session_id, model, targets, spins).catch((cause: unknown) => {
        setFieldsAsked(false);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  const requestInteraction = useCallback(
    (model: string, spins: Readonly<Record<string, number>>) => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setInteractionSince(Date.now());
      socket.requestInteraction(base.session_id, model, spins).catch((cause: unknown) => {
        setInteractionSince(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  return { orbitals, orbitalsSince, requestOrbitals, fields, fieldsAsked, requestOrbital, interaction, interactionSince, requestInteraction };
}
