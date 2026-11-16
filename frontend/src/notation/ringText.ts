import type {RingInfo, SystemInfo} from "../api/types/metadata";
import type {Span} from "../api/types/span";

/** How a ring's bonds are written, which is not what the ring is: benzene reads the same either way. */
export function notationOf(ring: RingInfo): string {
  if (ring.notation === "aromatic") {
    return "aromatic";
  }
  if (ring.notation === "mixed") {
    return "mixed (some bonds aromatic, some not)";
  }
  // a ring written without aromatic bonds is a Kekulé structure only where it is a pi system at all
  return ring.system === null ? "plain" : "Kekulé";
}

/** A system's electrons, saying where they are shared with the rings it is fused with. */
export function electronsOf(system: SystemInfo): string {
  return system.ring_count > 1
    ? `${system.pi_electrons} over a system of ${system.ring_count} fused rings`
    : String(system.pi_electrons);
}

/** Where an atom of the ring is written in the ring's own SMILES. */
export function spanIn(ring: RingInfo, atom: number): Span | null {
  return ring.spans[ring.atoms.indexOf(atom)] ?? null;
}
