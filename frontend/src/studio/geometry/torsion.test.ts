import {describe, expect, it} from "vitest";
import type {SessionState} from "../session/messages";
import {StateIndex} from "./StateIndex";
import {defaultReference, referencesAt, torsion} from "./torsion";

type Place = [string, string, [number, number, number]];

function build(places: Place[], joins: [string, string][]): StateIndex {
  const state = {
    atoms: places.map(([id, element, [x, y, z]]) => ({ id, element, isotope: 0, charge: 0, aromatic: false, was_implicit: false, position: { x, y, z } })),
    bonds: joins.map(([first, second], i) => ({ id: `b${i}`, first, second, order: "SINGLE", aromatic: false })),
    fragments: [{ key: "f", name: "m", atoms: places.map(([id]) => id) }],
    locked: [],
  } as unknown as SessionState;
  return new StateIndex(state);
}

/** a on b, b-c along x, d on c, and h on d; torsion a-b-c-d starts at 0 (cis). */
function butane(): StateIndex {
  return build(
    [["a", "C", [0, 1, 0]], ["b", "C", [0, 0, 0]], ["c", "C", [1, 0, 0]], ["d", "C", [1, 1, 0]], ["h", "H", [1, 1, 1]]],
    [["a", "b"], ["b", "c"], ["c", "d"], ["d", "h"]],
  );
}

describe("torsion", () => {
  const b = { x: 0, y: 0, z: 0 };
  const c = { x: 1, y: 0, z: 0 };
  const a = { x: 0, y: 1, z: 0 };

  it("is zero for atoms on the same side, and 180 for opposite sides", () => {
    expect(torsion(a, b, c, { x: 1, y: 1, z: 0 })).toBeCloseTo(0);
    expect(Math.abs(torsion(a, b, c, { x: 1, y: -1, z: 0 }))).toBeCloseTo(180);
  });

  it("is positive when the far atom is clockwise seen along the bond", () => {
    // looking along +x with y up, +z is to the right
    expect(torsion(a, b, c, { x: 1, y: 0, z: 1 })).toBeCloseTo(90);
    expect(torsion(a, b, c, { x: 1, y: 0, z: -1 })).toBeCloseTo(-90);
  });
});

describe("references", () => {
  it("lists the neighbours at an end other than the far atom, heaviest first", () => {
    const index = build(
      [["x", "C", [0, 0, 0]], ["y", "C", [1, 0, 0]], ["h", "H", [0, 1, 0]], ["o", "O", [0, 0, 1]], ["n", "N", [0, -1, 0]]],
      [["x", "y"], ["x", "h"], ["x", "o"], ["x", "n"]],
    );
    expect(referencesAt(index, index.bonds.get("b0")!, "first")).toEqual(["o", "n", "h"]);
  });

  it("has no default where an end has nothing else bonded to it", () => {
    const index = butane();
    expect(defaultReference(index, index.bonds.get("b1")!)).toEqual({ first: "a", second: "d" });
    expect(defaultReference(index, index.bonds.get("b0")!)).toBeNull();
  });
});
