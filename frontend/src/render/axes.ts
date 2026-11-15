import { ArrowHelper, Group, Vector3 } from "three";

/** How long each axis arrow is, in angstroms. */
export const AXIS_LENGTH = 2.5;

/**
 * Makes a set of coordinate axes.
 *
 * @returns a group centred on its origin, for the caller to place
 */
export function makeAxes(): Group {
  const colours = [0xe41a1c, 0x2ca02c, 0x1f4fd8];
  const axes = new Group();
  [new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1)].forEach((direction, i) => {
    axes.add(new ArrowHelper(direction, new Vector3(), AXIS_LENGTH, colours[i], 0.5, 0.36));
  });
  return axes;
}
