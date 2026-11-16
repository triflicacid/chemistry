import {ballRadius, type Style} from "../../../render/bonds";

/** How big the ball at each end of a bond is in the sticks style, which is as thick as the sticks so that they join round. */
export const STICK_JOINT = 0.15;

/** How big the bead is that shows a hovered or selected atom where the style draws no ball of its own, in angstroms. */
export const BEAD = 0.2;

/** How big the invisible sphere is that an atom is picked by in the styles that draw it small or not at all, in angstroms. */
export const PICK_ATOM = 0.3;

/** How thick the invisible cylinder is that a bond is picked by in those styles, in angstroms. */
export const PICK_BOND = 0.13;

/**
 * @param style how the molecule is drawn
 * @returns whether it draws atoms and bonds too slight to hover or click, so that they are picked by something larger
 */
export function isThin(style: Style): boolean {
  return style === "sticks" || style === "wireframe";
}

/**
 * @param radius the atom's van der Waals radius
 * @param style how the molecule is drawn
 * @param bonded whether any bond shown touches the atom
 * @returns the radius of the ball drawn for it, which is zero where there is none
 */
export function atomRadius(radius: number, style: Style, bonded: boolean): number {
  return style === "sticks" && bonded ? STICK_JOINT : ballRadius(radius, style, bonded);
}
