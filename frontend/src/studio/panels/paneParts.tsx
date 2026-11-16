import type {MouseEvent, ReactNode} from "react";
import {type Item, keyOf} from "../models/items";
import type {Models} from "../models/models";

interface LinksProps {
  models: Models;
  links: { item: Item; label: string }[];
}

/** A row of buttons that each name an item. */
export function Links({ models, links }: LinksProps) {
  const { selection, hover } = models;
  if (links.length === 0) {
    return <p className="panel-empty">None.</p>;
  }
  return (
    <ul className="links">
      {links.map(({ item, label }) => (
        <li key={keyOf(item)}>
          <button
            type="button"
            className="link"
            onClick={(event) => selection.select(item, additive(event))}
            onPointerEnter={() => hover.hover(item)}
            onPointerLeave={() => hover.hover(null)}
          >
            {label}
          </button>
        </li>
      ))}
    </ul>
  );
}

interface PropertyProps {
  name: string;
  value: ReactNode;
  mono?: boolean;
}

/** One labelled row of a `.properties` list. */
export function Property({ name, value, mono = false }: PropertyProps) {
  return (
    <>
      <dt>{name}</dt>
      <dd className={mono ? "mono" : undefined} title={typeof value === "string" ? value : undefined}>
        {value}
      </dd>
    </>
  );
}

function additive(event: MouseEvent): boolean {
  return event.shiftKey || event.ctrlKey || event.metaKey;
}

/** Writes a charge with its sign, so zero is `0`, one is `+1` and minus one is `-1`. */
export function signed(charge: number): string {
  return charge > 0 ? `+${charge}` : String(charge);
}
