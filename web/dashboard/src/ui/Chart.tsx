import { useEffect, useRef, useState } from "react";
import { areaPath, gridPath, monthTicks, Scale, squaresPath, stepPath, valueAxis, type Point } from "./charts";

const LEFT = 36;
const BOTTOM = 26;

export interface ChartProps {
  height: number;
  xMin: number;
  xMax: number;
  /** Small muted squares: every workout's set 1, or every weigh-in. */
  dots: Point[];
  /** The stepped primary line, oldest first. */
  steps: Point[];
  /** A flat lead-in from the left edge (the best before the range). */
  lead?: Point | null;
  /** Tint under the line (progress) or not (weight). */
  tint: boolean;
  /** Mark each step with a square point (progress records). */
  stepPoints: boolean;
  /** A dashed goal line (body weight). */
  goal?: number | null;
  minStep?: number;
  formatTick: (value: number) => string;
  monthLabel: (month: string) => string;
  label: string;
}

/** Width follows the container, so the chart fills a desktop column or a phone. */
function useWidth<T extends HTMLElement>(): [React.RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry!.contentRect.width)));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return [ref, width];
}

export function StepChart(props: ChartProps) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const plot = { width: Math.max(0, width - LEFT), height: props.height - BOTTOM };
  const values = [...props.dots, ...props.steps, ...(props.lead ? [props.lead] : [])].map((p) => p.y);
  if (props.goal != null) values.push(props.goal);
  const axis = valueAxis(values, props.minStep ?? 1);
  const scale = new Scale(plot, props.xMin, props.xMax, axis);
  const line = stepPath(scale, props.steps, props.lead ?? undefined);
  const bottom = plot.height - 1;
  const last = props.steps[props.steps.length - 1];
  const points = props.stepPoints ? props.steps.slice(0, -1) : [];

  return (
    <div ref={ref} className="chart-area" role="img" aria-label={props.label} style={{ height: props.height }}>
      {width > 0 && (
        <>
          {axis.ticks.map((v) => (
            <span key={v} className="chart-y" style={{ top: scale.y(v) + 4 }}>
              {props.formatTick(v)}
            </span>
          ))}
          <svg width={plot.width} height={plot.height} shapeRendering="crispEdges" aria-hidden="true" style={{ position: "absolute", left: LEFT, top: 4 }}>
            <path d={gridPath(scale, axis, plot.width)} stroke="var(--muted)" strokeOpacity={0.4} strokeWidth={2} strokeDasharray="2 4" fill="none" />
            {props.goal != null && <path d={`M0 ${scale.y(props.goal)}H${plot.width}`} stroke="var(--select)" strokeWidth={2} strokeDasharray="8 6" fill="none" />}
            {props.tint && <path d={areaPath(scale, line, props.steps, props.lead ?? undefined, bottom)} fill="var(--tint)" />}
            <path d={squaresPath(scale, props.dots, 4)} fill="var(--muted)" />
            <path d={line} fill="none" stroke="var(--primary)" strokeWidth={2} />
            <path d={squaresPath(scale, points, 6)} fill="var(--primary)" />
            <line x1={0} y1={bottom} x2={plot.width} y2={bottom} stroke="var(--muted)" strokeWidth={2} />
            {last && <rect x={scale.x(last.x) - 5} y={scale.y(last.y) - 5} width={10} height={10} fill="var(--select)" stroke="var(--outline)" strokeWidth={2} />}
          </svg>
          {monthTicks(scale, props.xMin, props.xMax, props.monthLabel, Math.max(2, Math.floor(plot.width / 60))).map((t) => (
            <span key={t.label + t.at} className="chart-x" style={{ left: LEFT + t.at, top: props.height - 16 }}>
              {t.label}
            </span>
          ))}
        </>
      )}
    </div>
  );
}
