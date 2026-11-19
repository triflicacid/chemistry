import {useEffect, useMemo} from "react";
import type {Span} from "../../api/types/span";
import {spanIn} from "../../notation/ringText";
import {SmilesText} from "../../notation/SmilesText";
import type {StudioRing} from "../chemistry/Chemistry";
import {indexOfId} from "../chemistry/Chemistry";
import type {Models} from "../models/models";
import {Mount} from "./MoleculeDetails";

interface RingSmilesProps {
  ring: StudioRing;
  models: Models;
  /** Atoms to keep marked in the ring's SMILES, by session id, each with the colour to mark it in. */
  marked?: readonly { id: string; colour: number }[];
  /** What a click does. */
  onPick?: () => void;
}

/** A ring written as SMILES. */
export function RingSmiles({ ring, models, marked = [], onPick }: RingSmilesProps) {
  const text = useMemo(() => new SmilesText(ring.info.smiles), [ring.info.smiles]);

  useEffect(() => {
    text.highlight(
      marked.flatMap(({ id, colour }) => {
        const index = indexOfId(id, "a");
        const span: Span | null = index === null ? null : spanIn(ring.info, index);
        return span ? [{ span, colour }] : [];
      }),
    );
  }, [text, ring, marked]);

  useEffect(() => {
    const { hover } = models;
    const atomAt = (offset: number): string | null => {
      const position = ring.info.spans.findIndex((span) => offset >= span.start && offset < span.end);
      return position < 0 ? null : (ring.atoms[position] ?? null);
    };
    return text.listen({
      hover: (offset) => {
        const id = offset === null ? null : atomAt(offset);
        hover.hover(id === null ? { kind: "ring", id: ring.id } : { kind: "atom", id });
      },
      pick: () => {},
    });
  }, [text, ring, models]);

  return (
    <span
      className={onPick ? "ring-smiles pickable" : "ring-smiles"}
      onClick={onPick}
      onPointerEnter={() => models.hover.hover({ kind: "ring", id: ring.id })}
      onPointerLeave={() => models.hover.hover(null)}
    >
      <Mount element={text.element} />
    </span>
  );
}
