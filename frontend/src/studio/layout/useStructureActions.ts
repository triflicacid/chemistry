import {useCallback, useMemo} from "react";
import {KEYS} from "../keys/keymap";
import {useHotkeys} from "../keys/useHotkeys";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import {useSessionContext} from "../session/SessionContext";

/** What the edit tool's sub-toolbar and shortcuts do to the selection. */
export interface StructureActions {
  /** Whether exactly two atoms are selected that are not bonded already. */
  canAddBond: boolean;
  /** Whether anything is selected that can be deleted. */
  canDelete: boolean;
  /** Bonds the two selected atoms, in the order they were selected, with the chosen order. */
  addBond: () => void;
  /** Deletes the selected atoms, or the selected bonds if no atom is selected. */
  deleteSelection: () => void;
}

/**
 * Offers the edit tool's actions on the selection, and listens for their keys while the edit tool is in use.
 *
 * @param models what is selected and which tool is in use
 * @returns the actions, and whether each can be done now
 */
export function useStructureActions({ selection, tools, index }: Models): StructureActions {
  const { addBond: bond, deleteAtoms, deleteBonds } = useSessionContext();
  const { items } = useObservable(selection);
  const { tool, bondOrder, carryHydrogens } = useObservable(tools);

  const pair = useMemo(() => {
    const atoms = items.flatMap((item) => (item.kind === "atom" ? [item.id] : []));
    return atoms.length === 2 && items.length === 2 ? (atoms as [string, string]) : null;
  }, [items]);

  const covered = useMemo(() => (index ? index.members(items) : null), [index, items]);

  const canAddBond = pair !== null && index !== null && !index.bondsOf(pair[0]).some((each) => each.first === pair[1] || each.second === pair[1]);
  const canDelete = covered !== null && (covered.atoms.size > 0 || covered.bonds.size > 0);

  const addBond = useCallback(() => {
    if (pair && canAddBond) {
      void bond(pair[0], pair[1], bondOrder);
    }
  }, [pair, canAddBond, bond, bondOrder]);

  const deleteSelection = useCallback(() => {
    if (!covered) {
      return;
    }
    if (covered.atoms.size > 0) {
      void deleteAtoms([...covered.atoms], carryHydrogens);
    } else if (covered.bonds.size > 0) {
      void deleteBonds([...covered.bonds]);
    }
  }, [covered, deleteAtoms, deleteBonds, carryHydrogens]);

  useHotkeys(
    tool === "edit"
      ? [
          { ...KEYS.addBond, run: addBond },
          {
            ...KEYS.deleteSelection,
            run: (event) => {
              event.preventDefault();
              deleteSelection();
            },
          },
        ]
      : [],
  );

  return { canAddBond, canDelete, addBond, deleteSelection };
}
