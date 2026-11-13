import type { AtomInfo, BondInfo, MoleculeMetadata, RingInfo, Span } from "../api/types";
import { FormulaView } from "./FormulaView";
import { SmilesText } from "./SmilesText";
import type { Highlight } from "./SmilesText";
import type { Selection } from "../store";
import type { MoleculeView, Pick as Target, Point } from "../view/MoleculeView";

/** Offset of the plaques from the pointer in pixels. */
const OFFSET = 14;

export class MoleculeInspector {
  private readonly plaques = document.createElement("div");
  private current: unknown = null;
  private readonly stopListening: () => void;

  constructor(
    private readonly view: MoleculeView,
    private readonly selection: Selection,
    private readonly smiles: SmilesText,
    private readonly canonical: SmilesText,
    private readonly formula: FormulaView,
    private readonly metadata: MoleculeMetadata,
  ) {
    this.plaques.className = "plaques";
    this.plaques.hidden = true;
    document.body.append(this.plaques);

    this.stopListening = view.listen({
      hover: (target, at) => this.hover(target, at),
      click: (target, additive) => this.click(target, additive),
    });
  }

  detach(): void {
    this.stopListening();
    this.clear();
    this.plaques.remove();
  }

  private hover(target: Target | null, at: Point): void {
    if (!target) {
      this.clear();
      return;
    }
    if (target.handle === this.current) {
      this.place(at);
      return;
    }

    this.clear();
    this.current = target.handle;

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
    this.view.setHighlights({
      hover: target,
      ends: bond ? atoms.map((member, position) => ({ atom: member.index, colour: firstColour + position })) : [],
    });
    const fragment = atoms[0]?.fragment;
    this.formula.highlightFragment(fragment ?? null);

    this.plaques.replaceChildren(this.describe(target, atom, bond, atoms));
    if (rings.length > 0) {
      this.plaques.append(this.describeRings(target.kind, atoms, rings, firstColour));
    }
    // a molecule of one fragment is the fragment, so naming it would say nothing
    if (fragment !== undefined && this.metadata.fragments.length > 1) {
      this.plaques.append(this.describeFragment(fragment));
    }
    this.plaques.hidden = false;
    this.place(at);
  }

  private click(target: Target | null, additive: boolean): void {
    if (target && target.index !== null) {
      this.selection.toggle(target.kind, target.index, additive);
    } else if (!additive) {
      this.selection.clear();
    }
  }

  private clear(): void {
    this.plaques.hidden = true;
    this.plaques.replaceChildren();
    this.current = null;
    this.view.setHighlights({ hover: null, ends: [] });
    this.smiles.highlight([]);
    this.canonical.highlight([]);
    this.formula.highlightFragment(null);
  }

  /** Keeps the plaques next to the pointer, but on screen. */
  private place(at: Point): void {
    const { width, height } = this.plaques.getBoundingClientRect();
    const left = at.x + OFFSET + width > window.innerWidth ? at.x - OFFSET - width : at.x + OFFSET;
    const top = at.y + OFFSET + height > window.innerHeight ? at.y - OFFSET - height : at.y + OFFSET;
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
      rows.push(["Fragment", `${atom.fragment} (of ${this.metadata.fragments.length})`]);
    } else if (bond) {
      title = `Bond ${bond.index}`;
      rows.push(
        ["Order", bond.order],
        ["Between", ends.map((end) => `${end.element} ${end.index}`).join(" – ")],
        ["Written", this.written(bond)],
      );
    } else {
      // untraceable element
      const data = target.data;
      const hydrogen = target.kind === "atom" && data.element === "H";
      title = target.kind === "atom" ? (hydrogen ? "Implicit hydrogen" : `Atom ${data.element ?? ""}`) : "Inferred bond";
      rows.push([
        "Note",
        target.kind === "bond" || hydrogen ? "Inferred" : "Added by the drawing",
      ]);
      if (target.kind === "bond" && data.order) {
        rows.push(["Order", data.order]);
      }
    }

    const plaque = MoleculeInspector.plaque(title);
    plaque.append(MoleculeInspector.details(rows));
    return plaque;
  }

  /** The fragment the hovered atom or bond belongs to, and the unit of the formula it was gathered into. */
  private describeFragment(index: number): HTMLElement {
    const fragment = this.metadata.fragments[index];
    if (!fragment) {
      return MoleculeInspector.plaque(`Fragment ${index}`);
    }

    const rows: [string, string][] = [
      ["Formula", fragment.formula],
      ["SMILES", fragment.smiles],
    ];
    const units = this.metadata.formula.neutral_units;
    const unit = units.units.find((candidate) => candidate.fragments.includes(index));
    // a unit standing for the one fragment repeats the rows above, so it is only worth naming when counted
    if (unit && unit.count > 1) {
      rows.push(["In unit", `${units.text.slice(unit.span.start, unit.span.end)} — one of ${unit.count}`]);
    }

    const plaque = MoleculeInspector.plaque(`Fragment ${index} (of ${this.metadata.fragments.length})`);
    plaque.append(MoleculeInspector.details(rows));
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
      line.append(`Ring (${ring.size}) ${ring.formula} `);

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

  private static highlights(bond: Span[] | undefined, members: (Span | null)[], firstColour: number): Highlight[] {
    return [
      ...(bond ?? []).map((span) => ({ span, colour: 0 })),
      ...members.map((span, position) => ({ span, colour: firstColour + position })),
    ];
  }

  private static spanIn(ring: RingInfo, atom: number): Span | null {
    return ring.spans[ring.atoms.indexOf(atom)] ?? null;
  }

  private static details(rows: [string, string][]): HTMLElement {
    const list = document.createElement("dl");
    for (const [name, value] of rows) {
      const term = document.createElement("dt");
      term.textContent = name;
      const description = document.createElement("dd");
      description.textContent = value;
      list.append(term, description);
    }
    return list;
  }

  private static plaque(title: string): HTMLElement {
    const plaque = document.createElement("section");
    plaque.className = "plaque";
    const heading = document.createElement("h2");
    heading.textContent = title;
    plaque.append(heading);
    return plaque;
  }
}
