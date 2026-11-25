import {useRef, useState} from "react";
import {nearestIndex, niceTicks} from "../layout/chart";
import {useWidth} from "./useWidth";

/** One line of a chart. */
export interface ChartSeries {
  name: string;
  /** Which of the chart's colours it is drawn in, which belongs to what the line is and not to where it is in the list. */
  slot: 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8;
  /** Whether it is drawn faintly, to leave another line to stand out. */
  muted?: boolean;
  points: readonly { x: number; y: number }[];
}

export interface LineChartProps {
  /** What the chart is, for someone who cannot see it. */
  label: string;
  /** The lines, which all measure the same thing in the same unit, since the chart has one vertical axis. */
  series: readonly ChartSeries[];
  xTitle: string;
  yTitle: string;
  /** Writes a value of x, as in the tooltip and on the axis. */
  formatX?: (x: number) => string;
  /** Writes a value of y. */
  formatY?: (y: number) => string;
  /** Whether the vertical axis is logarithmic, which leaves out points that are not above zero. */
  logY?: boolean;
  /** Where a marker is, in x. */
  marker?: number | null;
  /** Makes the marker draggable: told where it has got to, which is the x of a point of the first series. */
  onMarker?: (x: number) => void;
  /** Told where the marker was let go. */
  onMarkerEnd?: (x: number) => void;
  /** A position to point out, such as the lowest energy, with what to call it. */
  reference?: { x: number; label: string } | null;
  /** How tall the chart is, in pixels. */
  height?: number;
}

const MARGIN = { left: 56, right: 14, top: 10, bottom: 34 };

/** The most points a line has a dot on each of. */
const DOTTED = 14;

/**
 * A line chart with one vertical axis, a crosshair and tooltip that follow the pointer, and optionally a marker to drag along the first line.
 */
export function LineChart({ label, series, xTitle, yTitle, formatX = defaultFormat, formatY = defaultFormat, logY = false, marker = null, onMarker, onMarkerEnd, reference = null, height = 180 }: LineChartProps) {
  const [box, width] = useWidth<HTMLDivElement>(480);
  const [hover, setHover] = useState<number | null>(null);
  const dragging = useRef(false);

  const lines = series.map((each) => ({ ...each, points: logY ? each.points.filter((point) => point.y > 0) : each.points }));
  const all = lines.flatMap((line) => line.points);
  if (all.length === 0) {
    return <div ref={box} className="line-chart line-chart-empty" role="img" aria-label={`${label}: nothing to show`} />;
  }

  const toY = (y: number) => (logY ? Math.log10(y) : y);
  const [xLow, xHigh] = extent(all.map((point) => point.x));
  const [yLow, yHigh] = extent(all.map((point) => toY(point.y)));
  const yTicks = logY ? decades(yLow, yHigh) : niceTicks(yLow, yHigh, 4);
  const xTicks = niceTicks(xLow, xHigh, Math.max(2, Math.floor(width / 90)));
  const top = Math.max(yTicks.at(-1) ?? yHigh, yHigh);
  const bottom = Math.min(yTicks[0] ?? yLow, yLow);
  const left = Math.min(xTicks[0] ?? xLow, xLow);
  const right = Math.max(xTicks.at(-1) ?? xHigh, xHigh);

  const inner = { w: Math.max(1, width - MARGIN.left - MARGIN.right), h: Math.max(1, height - MARGIN.top - MARGIN.bottom) };
  const px = (x: number) => MARGIN.left + (right > left ? ((x - left) / (right - left)) * inner.w : inner.w / 2);
  const level = (tick: number) => MARGIN.top + (top > bottom ? (1 - (tick - bottom) / (top - bottom)) * inner.h : inner.h / 2);
  const py = (y: number) => level(toY(y));

  const first = lines[0]?.points ?? [];
  const xs = first.map((point) => point.x);
  const along = (event: { clientX: number }) => {
    const rect = box.current?.getBoundingClientRect();
    if (!rect) {
      return null;
    }
    const at = nearestIndex(xs, left + ((event.clientX - rect.left - MARGIN.left) / inner.w) * (right - left));
    const x = xs[at];
    return x === undefined ? null : { at, x };
  };

  const point = hover === null ? null : (first[hover] ?? null);
  const markerPoint = marker === null ? undefined : first[nearestIndex(xs, marker)];
  const interactive = onMarker !== undefined;

  return (
    <div ref={box} className="line-chart" style={{ height }}>
      <svg
        width={width}
        height={height}
        role="img"
        aria-label={label}
        className={interactive ? "line-chart-draggable" : undefined}
        onPointerMove={(event) => {
          const near = along(event);
          setHover(near ? near.at : null);
          if (dragging.current && near) {
            onMarker?.(near.x);
          }
        }}
        onPointerLeave={() => setHover(null)}
        onPointerDown={(event) => {
          const near = along(event);
          if (interactive && near) {
            dragging.current = true;
            event.currentTarget.setPointerCapture(event.pointerId);
            onMarker?.(near.x);
          }
        }}
        onPointerUp={(event) => {
          if (dragging.current) {
            dragging.current = false;
            event.currentTarget.releasePointerCapture(event.pointerId);
            const near = along(event);
            if (near) {
              onMarkerEnd?.(near.x);
            }
          }
        }}
      >
        {yTicks.map((tick) => (
          <g key={`y${tick}`}>
            <line className="chart-grid" x1={MARGIN.left} x2={MARGIN.left + inner.w} y1={level(tick)} y2={level(tick)} />
            <text className="chart-tick" x={MARGIN.left - 6} y={level(tick)} textAnchor="end" dominantBaseline="middle">
              {logY ? formatY(Math.pow(10, tick)) : formatY(tick)}
            </text>
          </g>
        ))}
        {xTicks.map((tick) => (
          <text key={`x${tick}`} className="chart-tick" x={px(tick)} y={MARGIN.top + inner.h + 14} textAnchor="middle">
            {formatX(tick)}
          </text>
        ))}
        <line className="chart-axis" x1={MARGIN.left} x2={MARGIN.left + inner.w} y1={MARGIN.top + inner.h} y2={MARGIN.top + inner.h} />
        <text className="chart-title" x={MARGIN.left + inner.w / 2} y={height - 3} textAnchor="middle">
          {xTitle}
        </text>
        <text className="chart-title" transform={`translate(11 ${MARGIN.top + inner.h / 2}) rotate(-90)`} textAnchor="middle">
          {yTitle}
        </text>

        {reference && (
          <g>
            <line className="chart-reference" x1={px(reference.x)} x2={px(reference.x)} y1={MARGIN.top} y2={MARGIN.top + inner.h} />
            <text className="chart-tick" x={px(reference.x) + 4} y={MARGIN.top + 9}>
              {reference.label}
            </text>
          </g>
        )}

        {lines.map((line) => (
          <g key={line.name} className={`chart-series chart-series-${line.slot}${line.muted ? " chart-muted" : ""}`}>
            <path d={line.points.map((each, i) => `${i === 0 ? "M" : "L"}${px(each.x).toFixed(1)} ${py(each.y).toFixed(1)}`).join(" ")} />
            {line.points.length <= DOTTED && line.points.map((each) => <circle key={each.x} cx={px(each.x)} cy={py(each.y)} r={4} />)}
          </g>
        ))}

        {marker !== null && markerPoint && (
          <g className="chart-marker">
            <line x1={px(marker)} x2={px(marker)} y1={MARGIN.top} y2={MARGIN.top + inner.h} />
            <circle cx={px(marker)} cy={py(markerPoint.y)} r={5} />
          </g>
        )}

        {point && !dragging.current && (
          <g className="chart-hover">
            <line x1={px(point.x)} x2={px(point.x)} y1={MARGIN.top} y2={MARGIN.top + inner.h} />
            {lines.map((line) => {
              const at = line.points.find((each) => each.x === point.x);
              return at ? <circle key={line.name} className={`chart-series-${line.slot}`} cx={px(at.x)} cy={py(at.y)} r={4} /> : null;
            })}
          </g>
        )}
      </svg>

      {point && (
        <div className="chart-tooltip" style={{ left: Math.min(px(point.x) + 10, width - 150), top: MARGIN.top }} role="status">
          <strong>{formatX(point.x)}</strong>
          {lines.map((line) => {
            const at = line.points.find((each) => each.x === point.x);
            return at ? (
              <div key={line.name}>
                <span className={`chart-key chart-series-${line.slot}`}>●</span> {line.name} {formatY(at.y)}
              </div>
            ) : null;
          })}
        </div>
      )}

      {lines.length > 1 && (
        <ul className="chart-legend">
          {lines.map((line) => (
            <li key={line.name}>
              <span className={`chart-key chart-series-${line.slot}`}>●</span> {line.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * @param values numbers
 * @returns the least and greatest of them, which are the same if there is one
 */
function extent(values: number[]): [number, number] {
  return [Math.min(...values), Math.max(...values)];
}

/**
 * @param low the bottom of a range of logarithms
 * @param high the top of it
 * @returns every whole number of them from just below the bottom to just above the top, which are the powers of ten to mark
 */
function decades(low: number, high: number): number[] {
  const ticks: number[] = [];
  for (let power = Math.floor(low); power <= Math.ceil(high); power++) {
    ticks.push(power);
  }
  return ticks;
}

/**
 * @param value a number
 * @returns it to a few significant figures, in exponent form where it is very small or very large
 */
function defaultFormat(value: number): string {
  const size = Math.abs(value);
  return size !== 0 && (size < 0.001 || size >= 100000) ? value.toExponential(1) : String(Number(value.toPrecision(4)));
}
