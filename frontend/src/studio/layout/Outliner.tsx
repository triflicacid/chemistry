import { type CSSProperties, type MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import { type Item, keyOf } from "../selection/items";
import type { Models } from "../selection/models";
import { useObservable } from "../selection/observable";
import type { SessionState } from "../session/messages";

export interface OutlinerProps {
  state: SessionState | null;
  models: Models;
}

/**
 * Lists what the scene holds: its molecules, and their atoms and bonds when expanded.
 *
 * Molecules, atoms and bonds are selectable and hoverable. The "Atoms" and "Bonds" rows exist
 * only to open and close their lists. Whatever becomes active elsewhere is opened and scrolled to.
 */
export function Outliner({ state, models }: OutlinerProps) {
  const { selection, hover, index } = models;
  const selected = useObservable(selection);
  const hovered = useObservable(hover);
  const [open, setOpen] = useState<Record<string, boolean>>({});

  const rows = useRef(new Map<string, HTMLElement>());
  const revealing = useRef<string | null>(null);

  const chosen = useMemo(() => new Set(selected.items.map(keyOf)), [selected.items]);
  const active = selected.active;

  // reveal what became active, opening the lists it is in
  useEffect(() => {
    if (!active || !index) {
      return;
    }
    const fragment = index.fragmentOf(active);
    if (fragment === null || active.kind === "molecule") {
      revealing.current = keyOf(active);
      return;
    }
    const lists = [fragment, `${fragment}:${active.kind}s`];
    revealing.current = keyOf(active);
    setOpen((before) => (lists.every((key) => before[key]) ? before : { ...before, ...Object.fromEntries(lists.map((key) => [key, true])) }));
  }, [active, index]);

  // the row may only exist once the lists above it have opened, so scrolling waits until it does
  useEffect(() => {
    const key = revealing.current;
    const row = key ? rows.current.get(key) : undefined;
    if (row) {
      row.scrollIntoView({ block: "nearest" });
      revealing.current = null;
    }
  }, [open, active]);

  if (!state || !index) {
    return <p className="panel-empty">Nothing is open.</p>;
  }

  const toggle = (key: string) => setOpen((before) => ({ ...before, [key]: !before[key] }));

  const row = (item: Item, label: string, depth: number) => {
    const key = keyOf(item);
    const classes = ["tree-row", "tree-label"];
    if (item.kind !== "molecule") {
      classes.push("tree-leaf");
    }
    if (chosen.has(key)) {
      classes.push("selected");
    }
    if (active && keyOf(active) === key) {
      classes.push("active");
    }
    if (hovered && keyOf(hovered) === key) {
      classes.push("hovered");
    }
    return (
      <button
        type="button"
        className={classes.join(" ")}
        style={indent(depth)}
        aria-pressed={chosen.has(key)}
        ref={(element) => {
          if (element) {
            rows.current.set(key, element);
          } else {
            rows.current.delete(key);
          }
        }}
        onClick={(event) => {
          // a modified click on a molecule that has anything selected in it lets go of all of that, not just the molecule row
          const inside = (each: Item) => index.fragmentOf(each) === item.id;
          if (item.kind === "molecule" && additive(event) && selected.items.some(inside)) {
            selection.remove(inside);
          } else {
            selection.select(item, additive(event));
          }
        }}
        onPointerEnter={() => hover.hover(item)}
        onPointerLeave={() => hover.hover(null)}
      >
        {label}
        {item.kind === "atom" && state.locked.includes(item.id) && <PadlockMark />}
      </button>
    );
  };

  // a click that lands on no row is a click on the background, which clears the selection as it does in the viewport
  const background = (event: MouseEvent) => {
    if (!(event.target as HTMLElement).closest("button")) {
      selection.clear();
    }
  };

  return (
    <div className="tree-area" onClick={background}>
      <ul className="tree">
        <li className="tree-row tree-root">Scene</li>
        {state.fragments.map((fragment) => {
          const atoms = `${fragment.key}:atom`;
          const bonds = `${fragment.key}:bond`;
          const links = index.bondsInFragment(fragment.key);
          return (
            <li key={fragment.key}>
              <div className="tree-line">
                <Twisty depth={1} expanded={open[fragment.key] ?? false} onToggle={() => toggle(fragment.key)} />
                {row({ kind: "molecule", id: fragment.key }, `${fragment.name} - ${index.formula(fragment.key)}`, 0)}
              </div>
              {(open[fragment.key] ?? false) && (
                <ul>
                  <li>
                    <Disclosure
                      label={`Atoms (${fragment.atoms.length})`}
                      depth={2}
                      expanded={open[atoms] ?? false}
                      onToggle={() => toggle(atoms)}
                    />
                    {(open[atoms] ?? false) && (
                      <ul>
                        {fragment.atoms.map((id) => (
                          <li key={id}>{row({ kind: "atom", id }, index.label(id), 3)}</li>
                        ))}
                      </ul>
                    )}
                  </li>
                  <li>
                    <Disclosure
                      label={`Bonds (${links.length})`}
                      depth={2}
                      expanded={open[bonds] ?? false}
                      onToggle={() => toggle(bonds)}
                    />
                    {(open[bonds] ?? false) && (
                      <ul>
                        {links.map((bond) => (
                          <li key={bond.id}>{row({ kind: "bond", id: bond.id }, index.bondLabel(bond), 3)}</li>
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
    </div>
  );
}

/** Whether a click adds to the selection rather than replacing it: the same keys as in the viewport. */
function additive(event: MouseEvent): boolean {
  return event.shiftKey || event.ctrlKey || event.metaKey;
}

interface TwistyProps {
  depth: number;
  expanded: boolean;
  onToggle: () => void;
}

function Twisty({ depth, expanded, onToggle }: TwistyProps) {
  return (
    <button
      type="button"
      className="tree-toggle"
      style={indent(depth)}
      aria-expanded={expanded}
      title={expanded ? "Collapse" : "Expand"}
      onClick={onToggle}
    >
      {expanded ? "▾" : "▸"}
    </button>
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

/** A small padlock after a locked atom's name. */
function PadlockMark() {
  return (
    <svg className="lock-mark" viewBox="0 0 12 14" width="10" height="12" role="img" aria-label="locked">
      <path d="M3.2 6V4.2a2.8 2.8 0 0 1 5.6 0V6" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <rect x="1.2" y="6" width="9.6" height="7" rx="1.6" fill="currentColor" />
    </svg>
  );
}
