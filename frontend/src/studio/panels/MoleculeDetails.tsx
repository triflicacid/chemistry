import {type ReactNode, useEffect, useMemo, useRef, useState} from "react";
import type {Span} from "../../api/types/span";
import type {AtomInfo, MoleculeMetadata} from "../../api/types/metadata";
import type {FormulaInfo} from "../../api/types/formula";
import {FormulaView} from "../../notation/FormulaView";
import {type Highlight, SmilesText} from "../../notation/SmilesText";
import {indexOfId as indexOf} from "../chemistry/Chemistry";
import type {Item} from "../models/items";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {SessionFragment, SessionState} from "../session/messages";
import {startOf} from "./pieces";

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
export function Mount({element}: { element: HTMLElement }) {
  const host = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    host.current?.replaceChildren(element);
  }, [element]);
  return <span ref={host}/>;
}

/** A tiny button that puts text on the clipboard. */
export function CopyButton({text, label}: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const copy = () => {
    navigator.clipboard.writeText(text).then(
      () => {
        setCopied(true);
        window.clearTimeout(timer.current);
        timer.current = window.setTimeout(() => setCopied(false), 1200);
      },
      (error) => console.warn("Could not copy", error),
    );
  };

  return (
    <button type="button" className="copy-button" title={copied ? "Copied" : label} aria-label={label} onClick={copy}>
      <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
        {copied ? (
          <path d="M3 8.5l3.5 3.5L13 4.5" strokeLinecap="round" strokeLinejoin="round"/>
        ) : (
          <>
            <rect x="5.5" y="5.5" width="8" height="8" rx="1.5"/>
            <path d="M10.5 3.5v-.5a1.5 1.5 0 0 0-1.5-1.5H3.5A1.5 1.5 0 0 0 2 3v5.5A1.5 1.5 0 0 0 3.5 10H4" strokeLinecap="round"/>
          </>
        )}
      </svg>
    </button>
  );
}

/**
 * @param fragment the molecule
 * @returns its formulae
 */
function formulaOf(fragment: SessionFragment): FormulaInfo {
  const none = {text: "", units: []};
  return {
    molecular: fragment.formula.molecular,
    empirical: fragment.formula.empirical,
    condensed: fragment.formula.condensed ?? "",
    components: none,
    neutral_units: none,
  };
}

function FragmentFormula({fragment}: { fragment: SessionFragment }) {
  const view = useMemo(() => new FormulaView(formulaOf(fragment), 1), [fragment]);
  return (
    <>
      <dt>Formula</dt>
      <dd className="wrap"><Mount element={view.element}/></dd>
    </>
  );
}

/** A molecule's SMILES. */
function PlainFragment({fragment}: { fragment: SessionFragment }) {
  return (
    <>
      <dt>SMILES</dt>
      <dd className="wrap mono">{fragment.smiles}<CopyButton text={fragment.smiles} label="Copy SMILES"/></dd>
      {fragment.canonical_smiles !== null && (
        <>
          <dt>Canonical</dt>
          <dd className="wrap mono">{fragment.canonical_smiles}<CopyButton text={fragment.canonical_smiles} label="Copy canonical SMILES"/></dd>
        </>
      )}
      <FragmentFormula fragment={fragment}/>
    </>
  );
}

interface MoleculeDetailsProps {
  state: SessionState;
  models: Models;
  /** The one molecule to describe, or undefined for the whole of it. */
  fragment?: SessionFragment;
  children?: ReactNode;
}

/** The SMILES, canonical SMILES and formulae of the molecule, or of one of its parts. */
export function MoleculeDetails({state, models, fragment, children}: MoleculeDetailsProps) {
  const {chemistry, failed} = models.chemistry;
  const metadata = chemistry?.metadata ?? null;
  const item = useObservable(models.hover);

  const note = (text: string) => (
    <dd className="note-row"><p className="panel-empty">{text}</p></dd>
  );
  return (
    <dl className="properties">
      {fragment && (failed || !metadata) ? <PlainFragment fragment={fragment}/> : failed ? note("The molecule's formulae could not be loaded.") : !metadata ? note("Reading the molecule…") : (
        <Loaded state={state} metadata={metadata} item={item} models={models} fragment={fragment}/>
      )}
      {children}
    </dl>
  );
}

interface LoadedProps {
  state: SessionState;
  metadata: MoleculeMetadata;
  item: Item | null;
  models: Models;
  fragment?: SessionFragment;
}

function Loaded({state, metadata, item, models, fragment}: LoadedProps) {
  // a molecule is the piece of the whole's text it is written as
  const earlier = useMemo(() => (fragment ? state.fragments.slice(0, state.fragments.indexOf(fragment)) : []), [state, fragment]);
  const smilesStart = useMemo(
    () => (fragment ? startOf(metadata.smiles, fragment.smiles, earlier.map((each) => each.smiles)) : 0),
    [metadata, fragment, earlier],
  );
  const canonicalStart = useMemo(
    () => (fragment ? startOf(metadata.canonical_smiles, fragment.canonical_smiles, earlier.map((each) => each.canonical_smiles)) : 0),
    [metadata, fragment, earlier],
  );
  const smiles = useMemo(
    () => (fragment ? new SmilesText(fragment.smiles, "smiles", smilesStart ?? 0) : new SmilesText(metadata.smiles)),
    [metadata, fragment, smilesStart],
  );
  const canonical = useMemo(
    () => (fragment ? new SmilesText(fragment.canonical_smiles ?? "", "smiles", canonicalStart ?? 0) : new SmilesText(metadata.canonical_smiles)),
    [metadata, fragment, canonicalStart],
  );
  const formula = useMemo(
    () => (fragment ? new FormulaView(formulaOf(fragment), 1) : new FormulaView(metadata.formula, metadata.fragments.length)),
    [metadata, fragment],
  );

  useEffect(() => {
    const {hover, focus} = models;
    let hovering = false;
    const setHover = (next: Item | null) => {
      hovering = next !== null;
      hover.hover(next);
    };

    // an atom the SMILES does not write, such as a hydrogen the session counted, has no place in the text
    const atomAt = (offset: number, key: "span" | "canonical_span"): string | null => {
      const atom = metadata.atoms.find((candidate) => {
        const span = candidate[key];
        return span !== null && offset >= span.start && offset < span.end;
      });
      const id = atom && `a${atom.index + 1}`;
      return id && state.atoms.some((candidate) => candidate.id === id && candidate.element === atom.element) ? id : null;
    };
    const atomListener = (key: "span" | "canonical_span") => ({
      hover: (offset: number | null) => {
        const id = offset === null ? null : atomAt(offset, key);
        setHover(id === null ? null : {kind: "atom", id});
      },
      pick: (offset: number) => {
        const id = atomAt(offset, key);
        if (id !== null) {
          focus.focus([id]);
        }
      },
    });

    // the session's molecules are the metadata's fragments, found through the atoms they share
    const moleculesOf = (fragments: readonly number[]) =>
      state.fragments.filter((molecule) =>
        molecule.atoms.some((id) => {
          const index = indexOf(id, "a");
          const atom = index === null ? undefined : metadata.atoms[index];
          return atom !== undefined && fragments.includes(atom.fragment);
        }),
      );

    const stops = [
      ...(smilesStart === null ? [] : [smiles.listen(atomListener("span"))]),
      ...(canonicalStart === null ? [] : [canonical.listen(atomListener("canonical_span"))]),
      formula.listen({
        hover: (fragments) => {
          const [first] = fragments ? moleculesOf(fragments) : [];
          setHover(first ? {kind: "molecule", id: first.key} : null);
        },
        pick: (fragments) => focus.focus(moleculesOf(fragments).flatMap((molecule) => molecule.atoms)),
      }),
    ];
    return () => {
      stops.forEach((stop) => stop());
      if (hovering) {
        hover.hover(null);
      }
    };
  }, [metadata, state, models, smiles, canonical, formula, smilesStart, canonicalStart]);

  useEffect(() => {
    const ends: AtomInfo[] = [];
    let bondSpans: Span[] | undefined;
    let canonicalSpans: Span[] | undefined;
    let firstColour = 0;
    let ringOrMolecule: number | null = null;

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
    } else if (item?.kind === "ring") {
      const ring = models.chemistry.chemistry?.ring(item.id);
      if (ring) {
        const bonds = ring.info.bonds.flatMap((index) => metadata.bonds[index] ?? []);
        const members = ring.info.atoms.flatMap((index) => metadata.atoms[index] ?? []);
        bondSpans = [...bonds.flatMap((bond) => bond.spans), ...members.flatMap((atom) => atom.span ?? [])];
        canonicalSpans = [...bonds.flatMap((bond) => bond.canonical_spans), ...members.flatMap((atom) => atom.canonical_span ?? [])];
        ringOrMolecule = members[0]?.fragment ?? null;
      }
    } else if (item?.kind === "molecule") {
      const members = state.fragments.find((candidate) => candidate.key === item.id)?.atoms ?? [];
      const atoms = members.flatMap((id) => {
        const index = indexOf(id, "a");
        return index === null ? [] : (metadata.atoms[index] ?? []);
      });
      const bonds = metadata.bonds.filter((bond) => atoms.some((atom) => atom.index === bond.first));
      ringOrMolecule = atoms[0]?.fragment ?? null;
      // a fragment is the text between dots, so it runs from its first atom or bond to its last
      bondSpans = hull(atoms.map((atom) => atom.span), bonds.flatMap((bond) => bond.spans));
      canonicalSpans = hull(atoms.map((atom) => atom.canonical_span), bonds.flatMap((bond) => bond.canonical_spans));
    }

    if (smilesStart !== null) {
      smiles.highlight(highlights(bondSpans, ends.map((atom) => atom.span), firstColour));
    }
    if (canonicalStart !== null) {
      canonical.highlight(highlights(canonicalSpans, ends.map((atom) => atom.canonical_span), firstColour));
    }
    formula.highlightFragment(ends[0]?.fragment ?? ringOrMolecule);
  }, [item, metadata, state, models, smiles, canonical, formula, smilesStart, canonicalStart]);

  return (
    <>
      <dt>SMILES</dt>
      <dd className="wrap mono"><Mount element={smiles.element}/><CopyButton text={fragment?.smiles ?? metadata.smiles} label="Copy SMILES"/></dd>
      {(!fragment || fragment.canonical_smiles !== null) && (
        <>
          <dt>Canonical</dt>
          <dd className="wrap mono"><Mount element={canonical.element}/><CopyButton text={fragment?.canonical_smiles ?? metadata.canonical_smiles} label="Copy canonical SMILES"/></dd>
        </>
      )}
      <dt>Formula</dt>
      <dd className="wrap"><Mount element={formula.element}/></dd>
    </>
  );
}
