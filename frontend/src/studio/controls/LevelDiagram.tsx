import {choiceOf, covers, nameOf, place, toEv} from "../layout/orbitals";
import {niceTicks} from "../layout/chart";
import type {OrbitalChoice} from "../models/OrbitalModel";
import type {OrbitalChannel, OrbitalLevel} from "../session/messages";
import {useWidth} from "./useWidth";

/** A set of orbitals to draw. */
export interface DrawnChannel extends Omit<OrbitalChannel, "levels"> {
  levels: readonly OrbitalLevel[];
  title: string | null;
}

export interface LevelDiagramProps {
  /** The sets of orbitals to draw, side by side: one for each molecule, and two for each where the spins have a set each. */
  channels: readonly DrawnChannel[];
  /** The orbital that is chosen, or null if none is. It is drawn in every set that has one in that place. */
  selected: OrbitalChoice | null;
  /** How many orbitals are drawn, counted outward from the one chosen, or null for every one. */
  count: number | null;
  /** Told which orbital was chosen. */
  onSelect: (choice: OrbitalChoice) => void;
  /** How tall the diagram is, in pixels. */
  height?: number;
}

const MARGIN = { left: 52, right: 8, top: 24, bottom: 10 };

/** How tall the strip that takes a click is, so that a thin line is easy to hit. */
const HIT = 14;

/** The room each set of orbitals leaves beside its lines for their names, which is less where the sets are narrow. */
function namesRoom(section: number): number {
  return section > 220 ? 78 : section > 130 ? 52 : 0;
}

/** An orbital energy-level diagram. */
export function LevelDiagram({ channels, selected, count, onSelect, height = 240 }: LevelDiagramProps) {
  const [box, width] = useWidth<HTMLDivElement>(480);
  const energies = channels.flatMap((channel) => channel.levels.map((level) => toEv(level.energy)));
  const low = Math.min(...energies);
  const high = Math.max(...energies);
  const span = high > low ? high - low : 1;
  const ticks = niceTicks(low - span * 0.08, high + span * 0.08, 5);
  const bottom = Math.min(ticks[0] ?? low, low - span * 0.04);
  const top = Math.max(ticks.at(-1) ?? high, high + span * 0.04);

  const inner = { w: Math.max(1, width - MARGIN.left - MARGIN.right), h: Math.max(1, height - MARGIN.top - MARGIN.bottom) };
  const y = (ev: number) => MARGIN.top + (1 - (ev - bottom) / (top - bottom)) * inner.h;
  const section = inner.w / Math.max(1, channels.length);
  const room = namesRoom(section);

  return (
    <div ref={box} className="line-chart">
      <svg width={width} height={height} role="group" aria-label="Orbital energy levels">
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

        {channels.map((channel, c) => {
          const start = MARGIN.left + c * section;
          const lines = Math.max(1, section - room);
          const homo = channel.homo ?? -1;
          const placed = place(channel.levels);
          // the orbitals of one energy are named together, beside the first of them
          const names = new Map<number, string[]>();
          for (const level of placed) {
            names.set(level.index - level.column, [...(names.get(level.index - level.column) ?? []), nameOf(level.index, homo)]);
          }

          return (
            <g key={`${channel.fragment ?? ""}|${channel.spin}`}>
              {channel.title !== null && (
                <text className="chart-title" x={start + lines / 2} y={MARGIN.top - 10} textAnchor="middle">
                  {channel.title}
                </text>
              )}
              {placed.map((level) => {
                const slot = lines / level.columns;
                const x1 = start + level.column * slot + 3;
                const x2 = start + (level.column + 1) * slot - 3;
                const at = y(toEv(level.energy));
                const choice = choiceOf(channel, level.index);
                const chosen = covers(selected, count, choice);
                const character = level.pi === null ? "" : `, ${level.pi >= 0.5 ? "π" : "σ"} ${Math.round((level.pi >= 0.5 ? level.pi : 1 - level.pi) * 100)}%`;
                const label = `${channel.title ? `${channel.title}, ` : ""}orbital ${level.index}, ${nameOf(level.index, homo)}, ${toEv(level.energy).toFixed(2)} eV, ${level.occupancy} ${level.occupancy === 1 ? "electron" : "electrons"}${character}`;
                return (
                  <g
                    key={level.index}
                    className={`level${level.occupancy > 0 ? " level-occupied" : ""}${level.pi !== null && level.pi >= 0.5 ? " level-pi" : ""}${chosen ? " level-selected" : ""}`}
                    role="button"
                    tabIndex={0}
                    aria-label={label}
                    aria-pressed={chosen}
                    onClick={() => onSelect(choice)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        onSelect(choice);
                      }
                    }}
                  >
                    <title>{label}</title>
                    <rect className="level-hit" x={x1} y={at - HIT / 2} width={Math.max(1, x2 - x1)} height={HIT} />
                    <line className="level-line" x1={x1} x2={x2} y1={at} y2={at} />
                    {level.occupancy > 0 && (
                      <text className="level-electrons" x={(x1 + x2) / 2} y={at - 3} textAnchor="middle">
                        {level.occupancy === 2 ? "↑↓" : channel.spin === "beta" ? "↓" : "↑"}
                      </text>
                    )}
                  </g>
                );
              })}
              {room > 0 &&
                [...names].map(([first, group]) => {
                  const level = placed.find((each) => each.index === first);
                  return level ? (
                    <text key={first} className="chart-tick" x={start + lines + 6} y={y(toEv(level.energy))} dominantBaseline="middle">
                      {group.join(" ")}
                    </text>
                  ) : null;
                })}
            </g>
          );
        })}
      </svg>
    </div>
  );
}
