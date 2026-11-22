import {niceTicks} from "../layout/chart";
import {useWidth} from "./useWidth";

export interface BarPart {
  /** What the part is, for the legend and the tooltip. */
  name: string;
  /** Its size, which is negative if it hangs below the baseline. */
  value: number;
  /** Which of the chart's colours it is drawn in, which belongs to what the part is and not to where it sits. */
  slot: 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8;
}

export interface Bar {
  /** What the bar is, which a click reports. */
  key: string;
  /** What it is called under it. */
  label: string;
  parts: readonly BarPart[];
  /** More to say of it in the tooltip. */
  title?: string;
}

export interface BarChartProps {
  /** What the chart is, for someone who cannot see it. */
  label: string;
  bars: readonly Bar[];
  yTitle: string;
  /** Writes a value of y, as on the axis and in the tooltip. */
  formatY?: (y: number) => string;
  /** Told which bar was clicked. */
  onSelect?: (key: string) => void;
  /** The bars to pick out, which are drawn with an outline. */
  picked?: ReadonlySet<string>;
  /** Whether to list what the parts are beneath the chart, which is wanted wherever a bar has more than one. */
  legend?: boolean;
  /** How tall the chart is, in pixels. */
  height?: number;
}

const MARGIN = { left: 48, right: 10, top: 8, bottom: 56 };

/** How wide a bar is at most, in pixels, however few there are, so that a bar stays thin. */
const THICKEST = 28;

/** How much of its slot a bar fills. */
const FILL = 0.7;

/** Vertical bars, one for each of a list of things. */
export function BarChart({ label, bars, yTitle, formatY = defaultFormat, onSelect, picked, legend = false, height = 170 }: BarChartProps) {
  const [box, width] = useWidth<HTMLDivElement>(480);
  if (bars.length === 0) {
    return <div ref={box} className="line-chart line-chart-empty" role="img" aria-label={`${label}: nothing to show`} />;
  }

  const above = bars.map((bar) => bar.parts.reduce((sum, part) => sum + Math.max(0, part.value), 0));
  const below = bars.map((bar) => bar.parts.reduce((sum, part) => sum + Math.min(0, part.value), 0));
  const low = Math.min(0, ...below);
  const high = Math.max(0, ...above);
  const ticks = niceTicks(low, high > low ? high : low + 1, 4);
  const bottom = Math.min(ticks[0] ?? low, low);
  const top = Math.max(ticks.at(-1) ?? high, high);

  const inner = { w: Math.max(1, width - MARGIN.left - MARGIN.right), h: Math.max(1, height - MARGIN.top - MARGIN.bottom) };
  const slot = inner.w / bars.length;
  const thickness = Math.min(THICKEST, slot * FILL);
  const y = (value: number) => MARGIN.top + (top > bottom ? (1 - (value - bottom) / (top - bottom)) * inner.h : inner.h / 2);
  // every label is drawn where the bars are wide enough, and every few where they are not, so that they do not run into one another
  const every = Math.max(1, Math.ceil(14 / slot));
  const legendParts = [...new Map(bars.flatMap((bar) => bar.parts).map((part) => [part.name, part])).values()];

  return (
    <div ref={box} className="line-chart">
      <svg width={width} height={height} role="img" aria-label={label}>
        {ticks.map((tick) => (
          <g key={tick}>
            <line className="chart-grid" x1={MARGIN.left} x2={MARGIN.left + inner.w} y1={y(tick)} y2={y(tick)} />
            <text className="chart-tick" x={MARGIN.left - 6} y={y(tick)} textAnchor="end" dominantBaseline="middle">
              {formatY(tick)}
            </text>
          </g>
        ))}
        <line className="chart-axis" x1={MARGIN.left} x2={MARGIN.left + inner.w} y1={y(0)} y2={y(0)} />
        <text className="chart-title" transform={`translate(11 ${MARGIN.top + inner.h / 2}) rotate(-90)`} textAnchor="middle">
          {yTitle}
        </text>

        {bars.map((bar, i) => {
          const centre = MARGIN.left + slot * (i + 0.5);
          let up = 0;
          let down = 0;
          const tooltip = `${bar.label}: ${bar.parts.map((part) => `${part.name} ${formatY(part.value)}`).join(", ")}${bar.title ? `. ${bar.title}` : ""}`;
          return (
            <g
              key={bar.key}
              className={`bar${onSelect ? " bar-clickable" : ""}${picked?.has(bar.key) ? " bar-picked" : ""}`}
              role={onSelect ? "button" : undefined}
              tabIndex={onSelect ? 0 : undefined}
              aria-label={tooltip}
              onClick={onSelect ? () => onSelect(bar.key) : undefined}
              onKeyDown={
                onSelect
                  ? (event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        onSelect(bar.key);
                      }
                    }
                  : undefined
              }
            >
              <title>{tooltip}</title>
              <rect className="bar-hit" x={centre - slot / 2} y={MARGIN.top} width={slot} height={inner.h} />
              {bar.parts.map((part) => {
                const from = part.value >= 0 ? up : down;
                const to = from + part.value;
                if (part.value >= 0) {
                  up = to;
                } else {
                  down = to;
                }
                const [a, b] = [y(Math.max(from, to)), y(Math.min(from, to))];
                return part.value === 0 ? null : <rect key={part.name} className={`chart-bar chart-series-${part.slot}`} x={centre - thickness / 2} y={a} width={thickness} height={Math.max(0.5, b - a)} />;
              })}
              {i % every === 0 && (
                <text className="chart-tick" transform={`translate(${centre} ${MARGIN.top + inner.h + 12}) rotate(-40)`} textAnchor="end">
                  {bar.label}
                </text>
              )}
            </g>
          );
        })}
      </svg>

      {legend && legendParts.length > 1 && (
        <ul className="chart-legend chart-legend-flush">
          {legendParts.map((part) => (
            <li key={part.name}>
              <span className={`chart-key chart-series-${part.slot}`}>●</span> {part.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * @param value a number
 * @returns it to a few significant figures
 */
function defaultFormat(value: number): string {
  return String(Number(value.toPrecision(3)));
}
