import {angleMovers, defaultPair} from "../../../geometry/angle";
import {movers} from "../../../geometry/bondLength";
import type {Item} from "../../../models/items";
import {defaultReference} from "../../../geometry/torsion";
import type {MeasurementTarget} from "../../handles/BondHandleController";
import type {ModeOptions} from "./ToolMode";

/**
 * @param selection what is selected
 * @returns the one thing selected, or undefined if there is not exactly one
 */
function single(selection: readonly Item[]): Item | undefined {
  return selection.length === 1 ? selection[0] : undefined;
}

/**
 * @param moving the atoms a handle would move
 * @param locked the atoms that cannot be moved
 * @returns whether one of them is locked
 */
function anyLocked(moving: Iterable<string>, locked: ReadonlySet<string>): boolean {
  return [...moving].some((id) => locked.has(id));
}

/**
 * The handle the move tool offers on a selected bond.
 *
 * @param options the selection and settings
 * @param locked the atoms that cannot be moved
 * @returns the handle, or null for none
 */
export function lengthTarget(options: ModeOptions, locked: ReadonlySet<string>): MeasurementTarget | null {
  const item = single(options.selection);
  const bond = item?.kind === "bond" ? options.index?.bonds.get(item.id) : undefined;
  if (!options.index || !bond) {
    return null;
  }

  const moving = movers(options.index, bond, options.hold, false);
  if (!moving || anyLocked(moving, locked)) {
    return null;
  }
  return { mode: "length", bond: bond.id, hold: options.hold };
}

/**
 * The handle the rotate tool offers on what is inside a molecule.
 *
 * @param options the selection and settings
 * @param locked the atoms that cannot be moved
 * @returns the handle, or null for none
 */
export function internalTarget(options: ModeOptions, locked: ReadonlySet<string>): MeasurementTarget | null {
  const { index, hold, torsion, angle } = options;
  const item = single(options.selection);
  if (!index || !item) {
    return null;
  }

  if (item.kind === "atom") {
    const pair = angle?.centre === item.id ? angle : defaultPair(index, item.id);
    const moving = pair && angleMovers(index, item.id, pair, hold);
    return moving && !anyLocked(moving, locked) && pair ? { mode: "angle", centre: item.id, hold, from: pair.from, to: pair.to } : null;
  }

  const bond = item.kind === "bond" ? index.bonds.get(item.id) : undefined;
  if (!bond) {
    return null;
  }

  const moving = movers(index, bond, hold, true);
  if (!moving || anyLocked(moving, locked)) {
    return null;
  }

  // the Inspector says which atoms it measures from when it is showing this bond, and the heaviest neighbours are the default
  const measured = torsion?.bond === bond.id ? torsion : defaultReference(index, bond);
  return measured ? { mode: "turn", bond: bond.id, hold, from: measured.first, to: measured.second } : null;
}
