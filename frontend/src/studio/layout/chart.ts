/**
 * @param low the bottom of a range
 * @param high the top of it
 * @param count about how many ticks are wanted
 * @returns evenly spaced ticks at round numbers (1, 2 or 5 times a power of ten) that cover the range, which may start a little
 *     below it and end a little above
 */
export function niceTicks(low: number, high: number, count: number): number[] {
  if (!Number.isFinite(low) || !Number.isFinite(high) || count < 1) {
    return [];
  }
  if (high <= low) {
    return [low];
  }

  const rough = (high - low) / count;
  const power = Math.pow(10, Math.floor(Math.log10(rough)));
  const share = rough / power;
  // the smallest round step that is at least the rough one, with a little slack for the error in the logarithm
  const step = (share <= 1.0001 ? 1 : share <= 2.0001 ? 2 : share <= 5.0001 ? 5 : 10) * power;

  const ticks: number[] = [];
  const first = Math.floor(low / step) * step;
  for (let i = 0; ; i++) {
    // each tick is a whole number of steps from the first and then rounded, so that none carries the error of a sum
    const tick = Number((first + i * step).toPrecision(12));
    ticks.push(tick);
    if (tick >= high - step * 1e-9) {
      break;
    }
  }
  return ticks;
}

/**
 * @param xs the x of each point, in increasing order
 * @param x a position
 * @returns the index of the point nearest to it, or -1 if there are no points
 */
export function nearestIndex(xs: readonly number[], x: number): number {
  let best = -1;
  let gap = Infinity;
  xs.forEach((each, i) => {
    if (Math.abs(each - x) < gap) {
      best = i;
      gap = Math.abs(each - x);
    }
  });
  return best;
}

/**
 * @param points a value after each step of something that settles
 * @returns how far each is from the last, which is the figure that shows how fast it settles; the last itself is left out, being zero away from itself
 */
export function apart(points: readonly { x: number; y: number }[]): { x: number; y: number }[] {
  const settled = points.at(-1)?.y;
  return settled === undefined ? [] : points.slice(0, -1).map(({ x, y }) => ({ x, y: Math.abs(y - settled) }));
}
