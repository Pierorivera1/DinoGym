import { exerciseIcons } from "./icons";

// Dinocito's 8×8 face, the cursor (DESIGN.md › Cursor): a yellow fill under an outline.
function px(rows: string[]): string {
  let d = "";
  rows.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) if (row[x] === "#") d += `M${x} ${y}h1v1h-1z`;
  });
  return d;
}

const CURSOR_OUTLINE = px(["..####..", ".#....#.", "#......#", "#.#..#.#", "#......#", "#..##..#", ".#....#.", "..####.."]);
const CURSOR_FILL = px(["........", "..####..", ".######.", ".#.##.#.", ".######.", ".##..##.", "..####..", "........"]);

/**
 * The cursor always has its dark outline, as in Day (DESIGN.md). In Night it sits on a
 * small cream tile (theme.css › .cursor) instead of switching to a white outline.
 * [size] is the whole box; the face is drawn inside a 1/8 padding.
 */
export function Cursor({ size }: { size: number }) {
  const pad = Math.max(2, Math.round(size / 8));
  const face = size - 2 * pad;
  return (
    <span className="cursor" style={{ width: size, height: size, padding: pad }} aria-hidden="true">
      <svg viewBox="0 0 8 8" width={face} height={face} shapeRendering="crispEdges">
        <path d={CURSOR_FILL} fill="var(--cursor-fill)" />
        <path d={CURSOR_OUTLINE} fill="var(--cursor-outline)" />
      </svg>
    </span>
  );
}

/** A 16×16 pixel icon (icons.ts) in one colour. */
export function Icon({ path, size, color = "currentColor", style }: { path: string; size: number; color?: string; style?: React.CSSProperties }) {
  return (
    <svg viewBox="0 0 16 16" width={size} height={size} shapeRendering="crispEdges" aria-hidden="true" style={style}>
      <path d={path} fill={color} />
    </svg>
  );
}

export function exerciseIcon(iconKey: string | undefined): string {
  return exerciseIcons[iconKey ?? ""] ?? exerciseIcons.GENERIC!;
}
