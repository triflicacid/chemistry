import {niceTicks} from "../layout/chart";
import {nameOf, nearGap, toEv} from "../layout/orbitals";
import type {InteractionChannel, InteractionLevel, InteractionResult} from "../session/messages";
import {useWidth} from "./useWidth";

export interface InteractionDiagramProps {
  /** How the orbitals of everything together are made of those of each molecule, which must be a complete one. */
  report: InteractionResult;
  /** Which set of orbitals to draw, where there is one for each spin. */
  spin: "restricted" | "alpha" | "beta";
  /** How many orbitals either side of the gap are drawn in each calculation, or null to draw every one. */
  around: number | null;
  /** Names a molecule for the user, from an atom of it. */
  nameOfMolecule: (atom: string | null) => string | null;
  /** How tall the diagram is, in pixels. */
  height?: number;
}

const MARGIN = { left: 52, right: 10, top: 26, bottom: 10 };

/** The least thickness of a link and how much thicker the strongest is. */
const LEAST = 0.6;
const MOST = 3.4;

/** A molecular orbital interaction diagram. */
export function InteractionDiagram({ report, spin, around, nameOfMolecule, height = 280 }: InteractionDiagramProps) {
  const [box, width] = useWidth<HTMLDivElement>(480);
  const whole = report.parts[0];
  const molecules = report.parts.slice(1);

  /** The levels of a calculation that are drawn, with its set's gap. */
  const shown = (channels: readonly InteractionChannel[]) => {
    const channel = channels.find((each) => each.spin === spin);
    return channel ? { channel, levels: around === null ? channel.levels : nearGap(channel.levels, channel.homo ?? -1, around) } : null;
  };

  const middle = whole ? shown(whole.channels) : null;
  const sides = molecules.map((part) => ({ fragment: part.fragment, drawn: shown(part.channels) }));
  const energies = [...(middle?.levels ?? []), ...sides.flatMap((side) => side.drawn?.levels ?? [])].map((level) => toEv(level.energy));
  if (!middle || energies.length === 0) {
    return <p className="energy-note">There are no {spin} orbitals to draw.</p>;
  }

  const low = Math.min(...energies);
  const high = Math.max(...energies);
  const span = high > low ? high - low : 1;
  const ticks = niceTicks(low - span * 0.08, high + span * 0.08, 5);
  const bottom = Math.min(ticks[0] ?? low, low - span * 0.04);
  const top = Math.max(ticks.at(-1) ?? high, high + span * 0.04);

  const inner = { w: Math.max(1, width - MARGIN.left - MARGIN.right), h: Math.max(1, height - MARGIN.top - MARGIN.bottom) };
  const y = (ev: number) => MARGIN.top + (1 - (ev - bottom) / (top - bottom)) * inner.h;

  // the molecules are split to either side of the whole, the first half on the left
  const left = Math.ceil(sides.length / 2);
  const columns = sides.length + 1;
  const slot = inner.w / columns;
  const lineLength = Math.min(slot * 0.5, 90);
  const centreOf = (column: number) => MARGIN.left + slot * (column + 0.5);
  const placed = sides.map((side, i) => ({ ...side, column: i < left ? i : i + 1, index: i }));
  const wholeColumn = left;

  const segment = (column: number, level: InteractionLevel) => ({ x1: centreOf(column) - lineLength / 2, x2: centreOf(column) + lineLength / 2, y: y(toEv(level.energy)) });
  const visible = (levels: readonly InteractionLevel[] | undefined, index: number) => levels?.some((level) => level.index === index) ?? false;
  const strongest = Math.max(0.0001, ...report.links.filter((link) => link.spin === spin).map((link) => link.weight));

  return (
    <div ref={box} className="line-chart">
      <svg width={width} height={height} role="img" aria-label="Orbital interaction diagram">
        {ticks.map((tick) => (
          <g key={tick}>
            <line className="chart-grid" x1={MARGIN.left} x2={MARGIN.left + inner.w} y1={y(tick)} y2={y(tick)} />
            <text className="chart-tick" x={MARGIN.left - 6} y={y(tick)} textAnchor="end" dominantBaseline="middle">
              {tick}
            </text>
          </g>
        ))}
        <text className="chart-title" transform={`translate(11 ${MARGIN.top + inner.h / 2}) rotate(-90)`} textAnchor="middle">
          Energy (eV)
        </text>

        {report.links
          .filter((link) => link.spin === spin)
          .map((link) => {
            const side = placed.find((each) => each.fragment === link.fragment);
            const from = side?.drawn?.levels.find((level) => level.index === link.orbital);
            const to = middle.levels.find((level) => level.index === link.whole);
            if (!side || !from || !to || !visible(side.drawn?.levels, link.orbital) || !visible(middle.levels, link.whole)) {
              return null;
            }

            const [a, b] = [segment(side.column, from), segment(wholeColumn, to)];
            // a line leaves the near end of the molecule's level and meets the near end of the whole's
            const [x1, x2] = side.column < wholeColumn ? [a.x2, b.x1] : [a.x1, b.x2];
            return (
              <line
                key={`${link.fragment}|${link.orbital}|${link.whole}`}
                className={`link chart-series-${(side.index % 8) + 1}`}
                x1={x1}
                y1={a.y}
                x2={x2}
                y2={b.y}
                strokeWidth={LEAST + (MOST - LEAST) * Math.min(1, link.weight / strongest)}
              >
                <title>{`${nameOfMolecule(link.fragment) ?? "molecule"} ${nameOf(link.orbital, side.drawn?.channel.homo ?? -1)} is ${(link.weight * 100).toFixed(0)}% of ${nameOf(link.whole, middle.channel.homo ?? -1)} of the whole`}</title>
              </line>
            );
          })}

        <g>
          <text className="chart-title" x={centreOf(wholeColumn)} y={MARGIN.top - 10} textAnchor="middle">
            all together
          </text>
          {middle.levels.map((level) => {
            const at = segment(wholeColumn, level);
            return (
              <g key={level.index} className={`level${level.occupancy > 0 ? " level-occupied" : ""}`}>
                <title>{`${nameOf(level.index, middle.channel.homo ?? -1)}, ${toEv(level.energy).toFixed(2)} eV`}</title>
                <line className="level-line" x1={at.x1} x2={at.x2} y1={at.y} y2={at.y} />
              </g>
            );
          })}
        </g>

        {placed.map((side) => (
          <g key={side.fragment ?? side.index}>
            <text className="chart-title" x={centreOf(side.column)} y={MARGIN.top - 10} textAnchor="middle">
              {nameOfMolecule(side.fragment) ?? `molecule ${side.index + 1}`}
            </text>
            {side.drawn?.levels.map((level) => {
              const at = segment(side.column, level);
              return (
                <g key={level.index} className={`level${level.occupancy > 0 ? " level-occupied" : ""}`}>
                  <title>{`${nameOf(level.index, side.drawn?.channel.homo ?? -1)}, ${toEv(level.energy).toFixed(2)} eV`}</title>
                  <line className="level-line" x1={at.x1} x2={at.x2} y1={at.y} y2={at.y} />
                </g>
              );
            })}
          </g>
        ))}
      </svg>
    </div>
  );
}
