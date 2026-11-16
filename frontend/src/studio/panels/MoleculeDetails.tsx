import {useEffect, useMemo, useRef, useState} from "react";
import {ChemistryApi} from "../../api/ChemistryApi";
import type {Span} from "../../api/types/span";
import type {AtomInfo, MoleculeMetadata} from "../../api/types/metadata";
import {FormulaView} from "../../notation/FormulaView";
import {type Highlight, SmilesText} from "../../notation/SmilesText";
import type {Item} from "../models/items";
import type {HoverModel} from "../models/HoverModel";
import {useObservable} from "../models/observable";
import type {SessionState} from "../session/messages";

const api = new ChemistryApi();

async function metadataOf(state: SessionState): Promise<MoleculeMetadata> {
  for (const id of [state.saved_molecule_id, state.source_molecule_id]) {
    if (id) {
      try {
        const known = await api.metadata(id);
        if (known.smiles === state.smiles) {
          return known;
        }
      } catch {
        // fall through to making one
      }
    }
  }
  const created = await api.createMolecule(state.smiles);
  return api.metadata(created.id);
}

/** The session's atom or bond id as an index into the metadata, which numbers from zero. */
function indexOf(id: string, prefix: "a" | "b"): number | null {
  if (!id.startsWith(prefix)) {
    return null;
  }
  const number = Number(id.slice(1));
  return Number.isInteger(number) && number >= 1 ? number - 1 : null;
}

function highlights(bond: Span[] | undefined, members: (Span | null)[], firstColour: number): Highlight[] {
  return [
    ...(bond ?? []).map((span) => ({span, colour: 0})),
    ...members.map((span, position) => ({span, colour: firstColour + position})),
  ];
}

/** The one range that covers every given span. */
function hull(...groups: (Span | null)[][]): Span[] {
  const spans = groups.flat().filter((span): span is Span => span !== null);
  if (spans.length === 0) {
    return [];
  }
  return [{start: Math.min(...spans.map((span) => span.start)), end: Math.max(...spans.map((span) => span.end))}];
}

/** Mounts a DOM element built outside React. */
function Mount({element}: { element: HTMLElement }) {
  const host = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    host.current?.replaceChildren(element);
  }, [element]);
  return <span ref={host}/>;
}

interface MoleculeDetailsProps {
  state: SessionState;
  hover: HoverModel;
}

/** The molecule's SMILES, canonical SMILES and formulae. */
export function MoleculeDetails({state, hover}: MoleculeDetailsProps) {
  const [metadata, setMetadata] = useState<MoleculeMetadata | null>(null);
  const [failed, setFailed] = useState(false);
  const item = useObservable(hover);

  useEffect(() => {
    let current = true;
    setMetadata(null);
    setFailed(false);
    metadataOf(state)
      .then((found) => current && setMetadata(found))
      .catch((error) => {
        console.warn("Could not load the molecule's metadata", error);
        if (current) {
          setFailed(true);
        }
      });
    return () => {
      current = false;
    };
  }, [state.smiles, state.saved_molecule_id, state.source_molecule_id]);

  if (failed) {
    return <p className="panel-empty">The molecule's formulae could not be loaded.</p>;
  }
  if (!metadata) {
    return <p className="panel-empty">Reading the molecule…</p>;
  }
  return <Loaded state={state} metadata={metadata} item={item}/>;
}

interface LoadedProps {
  state: SessionState;
  metadata: MoleculeMetadata;
  item: Item | null;
}

function Loaded({state, metadata, item}: LoadedProps) {
  const smiles = useMemo(() => new SmilesText(metadata.smiles), [metadata]);
  const canonical = useMemo(() => new SmilesText(metadata.canonical_smiles), [metadata]);
  const formula = useMemo(() => new FormulaView(metadata.formula, metadata.fragments.length), [metadata]);

  useEffect(() => {
    const ends: AtomInfo[] = [];
    let bondSpans: Span[] | undefined;
    let canonicalSpans: Span[] | undefined;
    let firstColour = 0;
    let fragment: number | null = null;

    if (item?.kind === "atom") {
      const index = indexOf(item.id, "a");
      const atom = index === null ? undefined : metadata.atoms[index];
      const session = state.atoms.find((candidate) => candidate.id === item.id);
      // an atom the SMILES does not write, such as a hydrogen the session counted, has nothing to mark
      if (atom && session && session.element === atom.element) {
        ends.push(atom);
      }
    } else if (item?.kind === "bond") {
      const index = indexOf(item.id, "b");
      const bond = index === null ? undefined : metadata.bonds[index];
      const session = state.bonds.find((candidate) => candidate.id === item.id);
      if (bond && session && session.first === `a${bond.first + 1}` && session.second === `a${bond.second + 1}`) {
        bondSpans = bond.spans;
        canonicalSpans = bond.canonical_spans;
        firstColour = 1;
        for (const atom of [metadata.atoms[bond.first], metadata.atoms[bond.second]]) {
          if (atom) {
            ends.push(atom);
          }
        }
      }
    } else if (item?.kind === "molecule") {
      const members = state.fragments.find((candidate) => candidate.key === item.id)?.atoms ?? [];
      const atoms = members.flatMap((id) => {
        const index = indexOf(id, "a");
        return index === null ? [] : (metadata.atoms[index] ?? []);
      });
      const bonds = metadata.bonds.filter((bond) => atoms.some((atom) => atom.index === bond.first));
      fragment = atoms[0]?.fragment ?? null;
      // a fragment is the text between dots, so it runs from its first atom or bond to its last
      bondSpans = hull(atoms.map((atom) => atom.span), bonds.flatMap((bond) => bond.spans));
      canonicalSpans = hull(atoms.map((atom) => atom.canonical_span), bonds.flatMap((bond) => bond.canonical_spans));
    }

    smiles.highlight(highlights(bondSpans, ends.map((atom) => atom.span), firstColour));
    canonical.highlight(highlights(canonicalSpans, ends.map((atom) => atom.canonical_span), firstColour));
    formula.highlightFragment(ends[0]?.fragment ?? fragment);
  }, [item, metadata, state, smiles, canonical, formula]);

  return (
    <dl className="properties">
      <dt>SMILES</dt>
      <dd className="wrap mono"><Mount element={smiles.element}/></dd>
      <dt>Canonical</dt>
      <dd className="wrap mono"><Mount element={canonical.element}/></dd>
      <dt>Formula</dt>
      <dd className="wrap"><Mount element={formula.element}/></dd>
    </dl>
  );
}
