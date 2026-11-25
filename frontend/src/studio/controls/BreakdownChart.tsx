import {niceTicks} from "../layout/chart";
import {type Stack, TERMS} from "../layout/breakdown";
import {useWidth} from "./useWidth";

/** One bar: what it is of, and its energy stacked out from zero. */
export interface BreakdownRow {
  label: string;
  stack: Stack;
}

export interface BreakdownChartProps {
  /** What the chart is, for someone who cannot see it. */
  label: string;
  rows: readonly BreakdownRow[];
  /** The unit the stacks are in, for the axis. */
  unit: string;
}

const MARGIN = { left: 12, right: 14, top: 4, bottom: 26 };

/** How tall a bar is, which stays thin however much room there is. */
const BAR = 18;

/** The space the name of a bar takes above it. */
const NAME = 16;

/** The space between one bar and the next name. */
const GAP = 12;

/** The space left between the parts of a bar, which is the surface showing through. */
const SPACER = 2;

/** How round the outer end of a bar is. */
const ROUND = 4;

/**
 * Stacked horizontal bars, one for each interaction, whose parts that raise the energy run to the right of zero and those that
 * lower it run to the left, with a tick where they add up to. All the bars share one scale, so their sizes can be compared.
 */
export function BreakdownChart({ label, rows, unit }: BreakdownChartProps) {
  const [box, width] = useWidth<HTMLDivElement>(480);
  const low = Math.min(0, ...rows.map((row) => row.stack.low));
  const high = Math.max(0, ...rows.map((row) => row.stack.high));
  const ticks = niceTicks(low, high, Math.max(2, Math.floor(width / 100)));
  const left = Math.min(ticks[0] ?? low, low);
  const right = Math.max(ticks.at(-1) ?? high, high);

  const inner = Math.max(1, width - MARGIN.left - MARGIN.right);
  const x = (value: number) => MARGIN.left + (right > left ? ((value - left) / (right - left)) * inner : inner / 2);
  const height = MARGIN.top + rows.length * (NAME + BAR + GAP) - GAP + MARGIN.bottom;
  const axis = height - MARGIN.bottom;

  return (
    <div ref={box} className="line-chart">
      <svg width={width} height={height} role="img" aria-label={label}>
        {ticks.map((tick) => (
          <g key={tick}>
            <line className="chart-grid" x1={x(tick)} x2={x(tick)} y1={MARGIN.top} y2={axis} />
            <text className="chart-tick" x={x(tick)} y={axis + 13} textAnchor="middle">
              {tick}
            </text>
          </g>
        ))}
        <line className="chart-axis" x1={MARGIN.left} x2={MARGIN.left + inner} y1={axis} y2={axis} />
        <text className="chart-title" x={MARGIN.left + inner / 2} y={height - 2} textAnchor="middle">
          {unit}
        </text>

        {rows.map((row, i) => {
          const top = MARGIN.top + i * (NAME + BAR + GAP);
          return (
            <g key={row.label}>
              <text className="chart-name" x={MARGIN.left} y={top + 11}>
                {row.label}
              </text>
              {row.stack.segments.map((segment) => {
                const [a, b] = [x(Math.min(segment.from, segment.to)), x(Math.max(segment.from, segment.to))];
                // a part is inset by the spacer on each side that has a neighbour, and rounded only at the end of the stack
                const outward = segment.value >= 0 ? segment.to === row.stack.high : segment.to === row.stack.low;
                const start = a + (segment.value >= 0 && segment.from > 0 ? SPACER / 2 : 0);
                const stop = b - (segment.value < 0 && segment.from < 0 ? SPACER / 2 : 0);
                const wide = stop - start;
                return wide > 0 ? (
                  <path
                    key={segment.key}
                    className={`chart-bar chart-series-${segment.slot}`}
                    d={bar(start, top + NAME, wide, BAR, segment.value >= 0 ? (outward ? "right" : "none") : outward ? "left" : "none")}
                  >
                    <title>{`${segment.name} ${signed(segment.value)} ${unit}`}</title>
                  </path>
                ) : null;
              })}
              <line className="chart-net" x1={x(row.stack.net)} x2={x(row.stack.net)} y1={top + NAME - 3} y2={top + NAME + BAR + 3}>
                <title>{`Net ${signed(row.stack.net)} ${unit}`}</title>
              </line>
              <text className="chart-tick" x={x(row.stack.net)} y={top + NAME + BAR + 11} textAnchor="middle">
                net {signed(row.stack.net)}
              </text>
            </g>
          );
        })}
        <line className="chart-zero" x1={x(0)} x2={x(0)} y1={MARGIN.top} y2={axis} />
      </svg>

      <ul className="chart-legend chart-legend-flush">
        {TERMS.map((term) => (
          <li key={term.key}>
            <span className={`chart-key chart-series-${term.slot}`}>●</span> {term.name}
          </li>
        ))}
        <li>
          <span className="chart-key chart-net-key">|</span> net
        </li>
      </ul>
    </div>
  );
}

/**
 * @param x where the bar starts
 * @param y where its top is
 * @param width how wide it is
 * @param height how tall it is
 * @param round which end is rounded: the right, the left, or neither
 * @returns a path for a rectangle that is square at the baseline and rounded at the outer end of the stack
 */
function bar(x: number, y: number, width: number, height: number, round: "left" | "right" | "none"): string {
  const r = round === "none" ? 0 : Math.min(ROUND, width, height / 2);
  const right = x + width;
  const bottom = y + height;
  return round === "right"
    ? `M${x} ${y}H${right - r}Q${right} ${y} ${right} ${y + r}V${bottom - r}Q${right} ${bottom} ${right - r} ${bottom}H${x}Z`
    : round === "left"
      ? `M${right} ${y}H${x + r}Q${x} ${y} ${x} ${y + r}V${bottom - r}Q${x} ${bottom} ${x + r} ${bottom}H${right}Z`
      : `M${x} ${y}H${right}V${bottom}H${x}Z`;
}

/**
 * @param value a number
 * @returns it with a sign, to a tenth
 */
function signed(value: number): string {
  return `${value >= 0 ? "+" : "−"}${Math.abs(value).toFixed(1)}`;
}
