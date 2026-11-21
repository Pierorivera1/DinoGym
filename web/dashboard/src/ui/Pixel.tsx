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

export function Cursor({ size, fill = "var(--select)" }: { size: number; fill?: string }) {
  return (
    <svg viewBox="0 0 8 8" width={size} height={size} shapeRendering="crispEdges" aria-hidden="true">
      <path d={CURSOR_FILL} fill={fill} />
      <path d={CURSOR_OUTLINE} fill="var(--outline)" />
    </svg>
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
