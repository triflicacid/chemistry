import {useCallback, useEffect, useMemo} from "react";
import {ORGANIC_ELEMENTS} from "../geometry/elements";
import {KEYS} from "../keys/keymap";
import {useHotkeys} from "../keys/useHotkeys";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {BondOrder} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";
import {BOND_ORDERS} from "./viewportOptions";

/** What the edit tool's sub-toolbar and shortcuts do to the selection. */
export interface StructureActions {
  /** Whether exactly two atoms are selected that are not bonded already. */
  canAddBond: boolean;
  /** Whether anything is selected that can be deleted. */
  canDelete: boolean;
  /** Whether an atom is selected whose hydrogens can be filled in. */
  canFill: boolean;
  /**
   * The order the order control shows: that of the selected bonds if they all have one, which the tool is then set to, and
   * otherwise the order the tool makes bonds with.
   */
  order: BondOrder;
  /** Bonds the two selected atoms, in the order they were selected, with the chosen order. */
  addBond: () => void;
  /** Deletes the selected atoms, or the selected bonds if no atom is selected. */
  deleteSelection: () => void;
  /** Gives the selected atoms the hydrogens that fill their valence. */
  fill: () => void;
  /** Chooses an order: for the bonds the tool makes, and for the bonds that are selected, which it changes. */
  chooseOrder: (order: BondOrder) => void;
}

/**
 * Offers the edit tool's actions on the selection, and listens for their keys while the edit tool is in use.
 *
 * @param models what is selected and which tool is in use
 * @returns the actions, and whether each can be done now
 */
export function useStructureActions({ selection, hover, tools, index }: Models): StructureActions {
  const { addBond: bond, deleteAtoms, deleteBonds, fillValences, setBondOrder } = useSessionContext();
  const { items } = useObservable(selection);
  const hovered = useObservable(hover);
  const { tool, bondOrder, carryHydrogens } = useObservable(tools);

  const pair = useMemo(() => {
    const atoms = items.flatMap((item) => (item.kind === "atom" ? [item.id] : []));
    return atoms.length === 2 && items.length === 2 ? (atoms as [string, string]) : null;
  }, [items]);

  // only bonds that are selected themselves count: a molecule or a ring that is selected is not a request to reorder every bond in it
  const bondIds = useMemo(() => items.flatMap((item) => (item.kind === "bond" ? [item.id] : [])), [items]);
  const selectedOrder = useMemo((): BondOrder | null => {
    if (!index || bondIds.length === 0) {
      return null;
    }
    const orders = new Set(bondIds.map((id) => index.bonds.get(id)?.order));
    const [only] = orders;
    return orders.size === 1 && only !== undefined && BOND_ORDERS.some((offered) => offered.value === only) ? only : null;
  }, [index, bondIds]);
  const order = selectedOrder ?? bondOrder;

  // selecting bonds that share an order picks that order up, so what is made next is made with it
  useEffect(() => {
    if (tool === "edit" && selectedOrder !== null && selectedOrder !== bondOrder) {
      tools.bondWith(selectedOrder);
    }
  }, [tool, selectedOrder, bondOrder, tools]);

  const chooseOrder = useCallback(
    (next: BondOrder) => {
      if (bondIds.length > 0 && next !== selectedOrder) {
        void setBondOrder(bondIds, next);
      }
      // with bonds selected that share an order, the tool follows the order they end up with, which a refusal leaves as it was
      if (selectedOrder === null) {
        tools.bondWith(next);
      }
    },
    [bondIds, selectedOrder, setBondOrder, tools],
  );

  const covered = useMemo(() => (index ? index.members(items) : null), [index, items]);

  const canAddBond = pair !== null && index !== null && !index.bondsOf(pair[0]).some((each) => each.first === pair[1] || each.second === pair[1]);
  const canDelete = covered !== null && (covered.atoms.size > 0 || covered.bonds.size > 0);

  // the atoms the button is for: those of the selection that the server knows the valence of
  const fillable = useMemo(() => {
    if (!index || !covered) {
      return [];
    }
    return [...covered.atoms].filter((id) => ORGANIC_ELEMENTS.includes(index.atoms.get(id)?.element ?? ""));
  }, [index, covered]);
  const canFill = fillable.length > 0;

  const fill = useCallback(() => {
    if (fillable.length > 0) {
      void fillValences(fillable);
    }
  }, [fillable, fillValences]);

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

  // the atom or bond under the pointer is what the key is for, unless it is part of the selection, in which case the key is for
  // the selection as a whole, as it is in any editor
  const deleteUnderPointer = useCallback((): boolean => {
    if (!covered || (hovered?.kind !== "atom" && hovered?.kind !== "bond")) {
      return false;
    }
    if (hovered.kind === "atom" ? covered.atoms.has(hovered.id) : covered.bonds.has(hovered.id)) {
      return false;
    }

    if (hovered.kind === "atom") {
      void deleteAtoms([hovered.id], carryHydrogens);
    } else {
      void deleteBonds([hovered.id]);
    }
    return true;
  }, [covered, hovered, deleteAtoms, deleteBonds, carryHydrogens]);

  useHotkeys(
    tool === "edit"
      ? [
          { ...KEYS.addBond, run: addBond },
          // H fills the valence of what is selected, and with nothing to fill it is the Carry H switch it is for the move tool
          { ...KEYS.toggleCarry, run: () => (canFill ? fill() : tools.carry(!carryHydrogens)) },
          {
            ...KEYS.deleteSelection,
            run: (event) => {
              event.preventDefault();
              if (!deleteUnderPointer()) {
                deleteSelection();
              }
            },
          },
        ]
      : [],
  );

  return { canAddBond, canDelete, canFill, order, addBond, deleteSelection, fill, chooseOrder };
}
