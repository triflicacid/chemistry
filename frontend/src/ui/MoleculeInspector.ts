import type { AtomInfo, BondInfo, MoleculeMetadata, RingInfo, Span } from "../api/types";
import { SmilesText } from "./SmilesText";
import type { Highlight } from "./SmilesText";

/** What the mouse pointer is over. */
interface Target {
  element: Element;
  kind: "atom" | "bond";
  index: number | null;
}

/** Offset of the plaques from the pointer in pixels. */
const OFFSET = 14;

/** How far outside an atom or bond the pointer may stray before the plaques disappear in pixels. */
const PADDING = 12;

export class MoleculeInspector {
  private readonly plaques = document.createElement("div");
  private current: Element | null = null;
  private ends: Element[] = [];

  constructor(
    private readonly svg: SVGSVGElement,
    private readonly smiles: SmilesText,
    private readonly canonical: SmilesText,
    private readonly metadata: MoleculeMetadata,
  ) {
    this.plaques.className = "plaques";
    this.plaques.hidden = true;
    document.body.append(this.plaques);

    svg.addEventListener("pointerover", (event) => this.over(event));
    svg.addEventListener("pointermove", (event) => this.move(event));
    svg.addEventListener("pointerleave", () => this.clear());
    svg.addEventListener("click", (event) => this.click(event));
  }

  detach(): void {
    this.clear();
    this.plaques.remove();
  }

  private over(event: PointerEvent): void {
    const target = MoleculeInspector.targetOf(event);
    // leaving onto the bare canvas is left to move(), which allows the pointer some slack
    if (!target || target.element === this.current) {
      return;
    }

    this.clear();
    this.current = target.element;
    target.element.classList.add("hover");

    const atom = target.kind === "atom" && target.index !== null ? this.metadata.atoms[target.index] : undefined;
    const bond = target.kind === "bond" && target.index !== null ? this.metadata.bonds[target.index] : undefined;
    const atoms = (atom ? [atom] : bond ? [this.metadata.atoms[bond.first], this.metadata.atoms[bond.second]] : []).filter(
      (member): member is AtomInfo => member !== undefined,
    );
    const rings = ((atom ?? bond)?.rings ?? [])
      .map((ring) => this.metadata.rings[ring])
      .filter((ring): ring is RingInfo => ring !== undefined);

    // a bond itself gets colour 0, like a hovered atom, and its ends colours 1 and 2
    const firstColour = bond ? 1 : 0;
    this.smiles.highlight(MoleculeInspector.highlights(bond?.spans, atoms.map((member) => member.span), firstColour));
    this.canonical.highlight(
      MoleculeInspector.highlights(bond?.canonical_spans, atoms.map((member) => member.canonical_span), firstColour),
    );
    if (bond) {
      atoms.forEach((member, position) => {
        const end = this.svg.querySelector(`.atom[data-atom="${member.index}"]`);
        end?.classList.add(`hover-end-${firstColour + position}`);
        if (end) {
          this.ends.push(end);
        }
      });
    }
    this.plaques.replaceChildren(this.describe(target, atom, bond, atoms));
    if (rings.length > 0) {
      this.plaques.append(this.describeRings(target.kind, atoms, rings, firstColour));
    }
    this.plaques.hidden = false;
    this.place(event);
  }

  private move(event: PointerEvent): void {
    if (!this.current) {
      return;
    }
    if (MoleculeInspector.near(this.current, event)) {
      this.place(event);
    } else {
      this.clear();
    }
  }

  private click(event: MouseEvent): void {
    const target = MoleculeInspector.targetOf(event);
    if (target?.kind === "atom" && target.index !== null) {
      console.log("atom", this.metadata.atoms[target.index]);
    } else if (target?.kind === "bond" && target.index !== null) {
      console.log("bond", this.metadata.bonds[target.index]);
    }
  }

  private clear(): void {
    this.plaques.hidden = true;
    this.plaques.replaceChildren();
    this.current?.classList.remove("hover");
    this.current = null;
    this.ends.forEach((end) => end.classList.remove("hover-end-1", "hover-end-2"));
    this.ends = [];
    this.smiles.highlight([]);
    this.canonical.highlight([]);
  }

  /** Keeps the plaques next to the pointer, but on screen. */
  private place(event: MouseEvent): void {
    const { width, height } = this.plaques.getBoundingClientRect();
    const left = event.clientX + OFFSET + width > window.innerWidth ? event.clientX - OFFSET - width : event.clientX + OFFSET;
    const top = event.clientY + OFFSET + height > window.innerHeight ? event.clientY - OFFSET - height : event.clientY + OFFSET;
    this.plaques.style.left = `${Math.max(0, left)}px`;
    this.plaques.style.top = `${Math.max(0, top)}px`;
  }

  private describe(target: Target, atom?: AtomInfo, bond?: BondInfo, ends: AtomInfo[] = []): HTMLElement {
    const rows: [string, string][] = [];
    let title: string;

    if (atom) {
      title = `Atom ${atom.index}`;
      rows.push(["Element", atom.atomic_number === null ? atom.element : `${atom.element} (${atom.atomic_number})`]);
      if (atom.isotope !== null) {
        rows.push(["Isotope", String(atom.isotope)]);
      }
      if (atom.charge !== 0) {
        rows.push(["Charge", atom.charge > 0 ? `+${atom.charge}` : String(atom.charge)]);
      }
      rows.push(["Hydrogens", String(atom.hydrogens)], ["Valence", String(atom.valence)]);
      if (atom.aromatic) {
        rows.push(["Aromatic", "yes"]);
      }
      rows.push(["Fragment", String(atom.fragment)]);
    } else if (bond) {
      title = `Bond ${bond.index}`;
      rows.push(
        ["Order", bond.order],
        ["Between", ends.map((end) => `${end.element} ${end.index}`).join(" – ")],
        ["Written", this.written(bond)],
      );
    } else {
      // untraceable element
      const element = target.element as HTMLElement;
      const hydrogen = target.kind === "atom" && element.dataset.element === "H";
      title = target.kind === "atom" ? (hydrogen ? "Implicit hydrogen" : `Atom ${element.dataset.element ?? ""}`) : "Inferred bond";
      rows.push([
        "Note",
        target.kind === "bond" || hydrogen ? "Inferred" : "Added by the drawing",
      ]);
      if (target.kind === "bond" && element.dataset.order) {
        rows.push(["Order", element.dataset.order]);
      }
    }

    const plaque = MoleculeInspector.plaque(title);
    const list = document.createElement("dl");
    for (const [name, value] of rows) {
      const term = document.createElement("dt");
      term.textContent = name;
      const description = document.createElement("dd");
      description.textContent = value;
      list.append(term, description);
    }
    plaque.append(list);
    return plaque;
  }

  /** How the bond is written in the SMILES, or that only its atoms imply it. */
  private written(bond: BondInfo): string {
    return bond.spans.length === 0
      ? "inferred"
      : bond.spans.map((span) => this.metadata.smiles.slice(span.start, span.end)).join(" + ");
  }

  private describeRings(kind: Target["kind"], atoms: AtomInfo[], rings: RingInfo[], firstColour: number): HTMLElement {
    const subject = kind === "atom" ? "atom" : "bond";
    const plaque = MoleculeInspector.plaque(
      rings.length === 1 ? `This ${subject} is in a ring` : `This ${subject} is in ${rings.length} rings`,
    );

    for (const ring of rings) {
      const line = document.createElement("p");
      line.className = "ring";
      line.append(`Ring (${ring.size}): `);

      const text = new SmilesText(ring.smiles);
      text.highlight(atoms.map((atom, position) => ({
        span: MoleculeInspector.spanIn(ring, atom.index),
        colour: firstColour + position,
      })));
      line.append(text.element);
      plaque.append(line);
    }
    return plaque;
  }

  private static near(element: Element, event: MouseEvent): boolean {
    const box = element.getBoundingClientRect();
    return (
      event.clientX >= box.left - PADDING &&
      event.clientX <= box.right + PADDING &&
      event.clientY >= box.top - PADDING &&
      event.clientY <= box.bottom + PADDING
    );
  }

  private static highlights(bond: Span[] | undefined, members: (Span | null)[], firstColour: number): Highlight[] {
    return [
      ...(bond ?? []).map((span) => ({ span, colour: 0 })),
      ...members.map((span, position) => ({ span, colour: firstColour + position })),
    ];
  }

  private static spanIn(ring: RingInfo, atom: number): Span | null {
    return ring.spans[ring.atoms.indexOf(atom)] ?? null;
  }

  private static plaque(title: string): HTMLElement {
    const plaque = document.createElement("section");
    plaque.className = "plaque";
    const heading = document.createElement("h2");
    heading.textContent = title;
    plaque.append(heading);
    return plaque;
  }

  private static targetOf(event: Event): Target | null {
    const element = (event.target as Element | null)?.closest<SVGElement>(".atom, .bond");
    if (!element) {
      return null;
    }
    const kind = element.classList.contains("atom") ? "atom" : "bond";
    const index = element.dataset[kind];
    return { element, kind, index: index === undefined ? null : Number(index) };
  }
}
