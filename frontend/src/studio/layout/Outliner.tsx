import {CSSProperties, useState} from "react";
import type { SessionState } from "../session/messages";

export interface OutlinerProps {
  state: SessionState | null;
}

/** Lists what the scene holds: its molecules, and their atoms and bonds when expanded. */
export function Outliner({ state }: OutlinerProps) {
  const [open, setOpen] = useState<Record<string, boolean>>({});

  const toggle = (key: string) => setOpen((before) => ({ ...before, [key]: !before[key] }));

  if (!state) {
    return <p className="panel-empty">Nothing is open.</p>;
  }

  const labels = atomLabels(state);

  return (
    <ul className="tree" role="tree">
      <li className="tree-row tree-root">Scene</li>
      {state.fragments.map((fragment) => {
        const bonds = state.bonds.filter((bond) => fragment.atoms.includes(bond.first));
        return (
          <li key={fragment.key}>
            <Disclosure
              label={`${fragment.name} - ${formula(state, fragment.atoms)}`}
              depth={1}
              expanded={open[fragment.key] ?? false}
              onToggle={() => toggle(fragment.key)}
            />
            {(open[fragment.key] ?? false) && (
              <ul role="group">
                <li>
                  <Disclosure
                    label={`Atoms (${fragment.atoms.length})`}
                    depth={2}
                    expanded={open[`${fragment.key}:atoms`] ?? false}
                    onToggle={() => toggle(`${fragment.key}:atoms`)}
                  />
                  {(open[`${fragment.key}:atoms`] ?? false) && (
                    <ul role="group">
                      {fragment.atoms.map((id) => (
                        <li key={id} className="tree-row tree-leaf" style={indent(3)} role="treeitem">
                          {labels.get(id)}
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
                <li>
                  <Disclosure
                    label={`Bonds (${bonds.length})`}
                    depth={2}
                    expanded={open[`${fragment.key}:bonds`] ?? false}
                    onToggle={() => toggle(`${fragment.key}:bonds`)}
                  />
                  {(open[`${fragment.key}:bonds`] ?? false) && (
                    <ul role="group">
                      {bonds.map((bond) => (
                        <li key={bond.id} className="tree-row tree-leaf" style={indent(3)} role="treeitem">
                          {labels.get(bond.first)}
                          {dash(bond.order)}
                          {labels.get(bond.second)}
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              </ul>
            )}
          </li>
        );
      })}
    </ul>
  );
}

interface DisclosureProps {
  label: string;
  depth: number;
  expanded: boolean;
  onToggle: () => void;
}

function Disclosure({ label, depth, expanded, onToggle }: DisclosureProps) {
  return (
    <button type="button" className="tree-row tree-twisty" style={indent(depth)} aria-expanded={expanded} onClick={onToggle}>
      <span className="twisty">{expanded ? "▾" : "▸"}</span>
      {label}
    </button>
  );
}

function indent(depth: number): CSSProperties {
  return { paddingLeft: `${depth * 0.85}rem` };
}

/** Names each atom by its element and its place in the molecule, as `O1`, `H2`, `H3`. */
function atomLabels(state: SessionState): Map<string, string> {
  return new Map(state.atoms.map((atom, index) => [atom.id, `${atom.element}${index + 1}`]));
}

function dash(order: SessionState["bonds"][number]["order"]): string {
  switch (order) {
    case "DOUBLE":
      return "=";
    case "TRIPLE":
      return "#";
    case "QUADRUPLE":
      return "$";
    case "AROMATIC":
      return ":";
    default:
      return "-";
  }
}

/** Counts the elements of some atoms, carbon and hydrogen first, as a molecular formula. */
function formula(state: SessionState, atoms: string[]): string {
  const held = new Set(atoms);
  const counts = new Map<string, number>();
  state.atoms
    .filter((atom) => held.has(atom.id))
    .forEach((atom) => counts.set(atom.element, (counts.get(atom.element) ?? 0) + 1));

  const rest = [...counts.keys()].filter((element) => element !== "C" && element !== "H").sort();
  return ["C", "H", ...rest]
    .filter((element) => counts.has(element))
    .map((element) => `${element}${counts.get(element) === 1 ? "" : counts.get(element)}`)
    .join("");
}
