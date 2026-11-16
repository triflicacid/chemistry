import type {MoleculeMetadata, RingInfo, SystemInfo} from "../../api/types/metadata";
import type {SessionState} from "../session/messages";

/** A ring of the molecule. */
export interface StudioRing {
  /** `r1`, `r2`, ...: the ring's place in the metadata, counting from one. */
  readonly id: string;
  readonly info: RingInfo;
  /** The pi system the ring is part of. */
  readonly system: SystemInfo | null;
  /** The ring's atoms, in the order of `info.atoms`, which is also the order of `info.spans`. */
  readonly atoms: readonly string[];
  readonly bonds: readonly string[];
  /** The atoms and bonds of every ring fused into the same pi system, this ring's own included. */
  readonly systemAtoms: readonly string[];
  readonly systemBonds: readonly string[];
}

/** The session's atom or bond id as an index into the metadata, which numbers from zero. */
export function indexOfId(id: string, prefix: "a" | "b"): number | null {
  if (!id.startsWith(prefix)) {
    return null;
  }
  const number = Number(id.slice(1));
  return Number.isInteger(number) && number >= 1 ? number - 1 : null;
}

/** What the server knows about the molecule's chemistry. */
export class Chemistry {
  readonly rings: readonly StudioRing[];
  private readonly byId = new Map<string, StudioRing>();

  constructor(
    readonly metadata: MoleculeMetadata,
    readonly state: SessionState,
  ) {
    const atoms = new Map<number, string>();
    metadata.atoms.forEach((atom, index) => {
      const id = `a${index + 1}`;
      if (state.atoms.some((candidate) => candidate.id === id && candidate.element === atom.element)) {
        atoms.set(index, id);
      }
    });
    const bonds = new Map<number, string>();
    metadata.bonds.forEach((bond, index) => {
      const id = `b${index + 1}`;
      const first = atoms.get(bond.first);
      const second = atoms.get(bond.second);
      if (first && second && state.bonds.some((candidate) => candidate.id === id && candidate.first === first && candidate.second === second)) {
        bonds.set(index, id);
      }
    });

    const named = (indices: readonly number[], names: Map<number, string>): string[] | null => {
      const found = indices.map((index) => names.get(index));
      return found.every((name): name is string => name !== undefined) ? found : null;
    };
    const rings: StudioRing[] = [];
    for (const info of metadata.rings) {
      const ringAtoms = named(info.atoms, atoms);
      const ringBonds = named(info.bonds, bonds);
      if (!ringAtoms || !ringBonds) {
        continue;
      }
      const system = info.system === null ? null : (metadata.systems[info.system] ?? null);
      rings.push({
        id: `r${info.index + 1}`,
        info,
        system,
        atoms: ringAtoms,
        bonds: ringBonds,
        systemAtoms: (system && named(system.atoms, atoms)) ?? ringAtoms,
        systemBonds: (system && named(system.bonds, bonds)) ?? ringBonds,
      });
    }
    this.rings = rings;
    rings.forEach((ring) => this.byId.set(ring.id, ring));
  }

  /**
   * Finds a ring.
   *
   * @param id a ring identifier
   * @returns the ring, or undefined if there is none
   */
  ring(id: string): StudioRing | undefined {
    return this.byId.get(id);
  }

  /**
   * Finds the rings an atom or bond is part of.
   *
   * @param kind whether `id` names an atom or a bond
   * @param id the atom or bond identifier
   * @returns the rings, in the order the metadata lists them
   */
  ringsOf(kind: "atom" | "bond", id: string): StudioRing[] {
    return this.rings.filter((ring) => (kind === "atom" ? ring.atoms : ring.bonds).includes(id));
  }

  /**
   * Finds the rings that share a pi system with a ring.
   *
   * @param ring the ring
   * @returns every other ring fused into the same system
   */
  fusedWith(ring: StudioRing): StudioRing[] {
    return ring.info.system === null ? [] : this.rings.filter((other) => other !== ring && other.info.system === ring.info.system);
  }
}
