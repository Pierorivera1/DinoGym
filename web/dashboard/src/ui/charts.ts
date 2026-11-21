// SVG paths for the two charts (DESIGN.md › Chart): a stepped line, square points,
// dotted grid lines. Pure functions of the data and the plot size, so they are tested
// without a browser. No chart library (decision 111).

export interface Point {
  /** Day number (days since 1970-01-01, rules/days.ts). */
  x: number;
  y: number;
}

export interface Plot {
  width: number;
  height: number;
}

export interface Axis {
  min: number;
  max: number;
  ticks: number[];
}

export interface Tick {
  label: string;
  /** Pixel position along the axis. */
  at: number;
}

/** A y axis around [values] with whole-number or half ticks, at most about six of them. */
export function valueAxis(values: number[], minStep = 1): Axis {
  if (values.length === 0) return { min: 0, max: minStep * 2, ticks: [0, minStep, minStep * 2] };
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const span = Math.max(hi - lo, minStep);
  const steps = [0.5, 1, 2, 5, 10, 20, 25, 50, 100].filter((s) => s >= minStep);
  const step = steps.find((s) => span / s <= 5) ?? steps[steps.length - 1]!;
  const min = Math.max(0, Math.floor(lo / step) * step - (lo % step === 0 ? step : 0));
  const max = Math.ceil(hi / step) * step + (hi % step === 0 ? step : 0);
  const ticks: number[] = [];
  for (let v = min + step; v < max; v += step) ticks.push(Math.round(v * 100) / 100);
  return { min, max, ticks };
}

export class Scale {
  constructor(
    private readonly plot: Plot,
    private readonly xMin: number,
    private readonly xMax: number,
    private readonly axis: Axis,
    /** Room kept at the left and right edges so square points are not cut. */
    private readonly pad = 6,
  ) {}

  x(day: number): number {
    const span = Math.max(1, this.xMax - this.xMin);
    return Math.round(this.pad + ((day - this.xMin) / span) * (this.plot.width - 2 * this.pad));
  }

  y(value: number): number {
    const span = this.axis.max - this.axis.min;
    return Math.round(this.plot.height - 1 - ((value - this.axis.min) / span) * (this.plot.height - 2));
  }
}

/** Dotted horizontal lines, one per tick. */
export function gridPath(scale: Scale, axis: Axis, width: number): string {
  return axis.ticks.map((v) => `M0 ${scale.y(v)}H${width}`).join("");
}

/**
 * A stepped line through [points] (oldest first): flat until the next point, then
 * straight up or down. [startLevel] draws a flat lead-in from [startX] when the line
 * began before the visible range.
 */
export function stepPath(scale: Scale, points: Point[], start?: { x: number; y: number }): string {
  const all = start ? [start, ...points] : points;
  if (all.length === 0) return "";
  let d = `M${scale.x(all[0]!.x)} ${scale.y(all[0]!.y)}`;
  for (const p of all.slice(1)) d += `H${scale.x(p.x)}V${scale.y(p.y)}`;
  return d;
}

/** The tint under a stepped line, down to the x axis. */
export function areaPath(scale: Scale, line: string, points: Point[], start: Point | undefined, bottom: number): string {
  const first = start ?? points[0];
  if (!line || !first) return "";
  return `${line}V${bottom}H${scale.x(first.x)}Z`;
}

/** Squares of [size] centred on each point. */
export function squaresPath(scale: Scale, points: Point[], size: number): string {
  const half = size / 2;
  return points.map((p) => `M${scale.x(p.x) - half} ${scale.y(p.y) - half}h${size}v${size}h${-size}z`).join("");
}

/**
 * Month labels for an x range of day numbers: one per month whose middle is inside
 * the range, placed at that middle. [label] turns a "YYYY-MM" key into text.
 * When there are more than [max] months, every other one is dropped, then more.
 */
export function monthTicks(scale: Scale, xMin: number, xMax: number, label: (month: string) => string, max = 8): Tick[] {
  const months: { month: string; mid: number }[] = [];
  const first = new Date(xMin * 86_400_000);
  let y = first.getUTCFullYear();
  let m = first.getUTCMonth();
  for (;;) {
    const start = Date.UTC(y, m, 1) / 86_400_000;
    if (start > xMax) break;
    const mid = start + 14;
    if (mid >= xMin && mid <= xMax) months.push({ month: `${y}-${String(m + 1).padStart(2, "0")}`, mid });
    m++;
    if (m === 12) {
      m = 0;
      y++;
    }
  }
  const every = Math.max(1, Math.ceil(months.length / max));
  return months.filter((_, i) => (months.length - 1 - i) % every === 0).map((t) => ({ label: label(t.month), at: scale.x(t.mid) }));
}
