import { describe, expect, it } from "vitest";
import { areaPath, gridPath, monthTicks, Scale, squaresPath, stepPath, valueAxis } from "./charts";
import { dayNumber } from "../rules/days";

describe("valueAxis", () => {
  it("leaves a step of room around reps and ticks whole numbers", () => {
    expect(valueAxis([4, 5, 7])).toEqual({ min: 3, max: 8, ticks: [4, 5, 6, 7] });
  });

  it("does not go below zero", () => {
    expect(valueAxis([0, 2]).min).toBe(0);
  });

  it("uses a coarser step for a wide range", () => {
    const axis = valueAxis([20, 100]);
    expect(axis.ticks.length).toBeLessThanOrEqual(6);
    expect(axis.ticks.every((t) => t % 20 === 0)).toBe(true);
  });

  it("ticks halves for body weight when the range is narrow", () => {
    const axis = valueAxis([72.1, 72.9], 0.5);
    expect(axis.ticks).toContain(72.5);
    expect(axis.min).toBeLessThanOrEqual(72.1);
    expect(axis.max).toBeGreaterThanOrEqual(72.9);
  });

  it("has a default when there is no data", () => {
    expect(valueAxis([]).ticks.length).toBeGreaterThan(0);
  });
});

describe("paths", () => {
  const axis = { min: 0, max: 10, ticks: [5] };
  const scale = new Scale({ width: 112, height: 102 }, 0, 100, axis, 6);

  it("maps the ends of both axes to the plot's edges", () => {
    expect(scale.x(0)).toBe(6);
    expect(scale.x(100)).toBe(106);
    expect(scale.y(0)).toBe(101);
    expect(scale.y(10)).toBe(1);
  });

  it("steps flat, then up", () => {
    const line = stepPath(scale, [
      { x: 0, y: 4 },
      { x: 50, y: 6 },
    ]);
    expect(line).toBe("M6 61H56V41");
  });

  it("starts with a flat lead-in when the line began before the range", () => {
    expect(stepPath(scale, [{ x: 50, y: 6 }], { x: 0, y: 4 })).toBe("M6 61H56V41");
  });

  it("closes the tint down to the axis", () => {
    const pts = [{ x: 0, y: 4 }];
    expect(areaPath(scale, stepPath(scale, pts), pts, undefined, 101)).toBe("M6 61V101H6Z");
    expect(areaPath(scale, "", [], undefined, 101)).toBe("");
  });

  it("draws centred squares and grid lines", () => {
    expect(squaresPath(scale, [{ x: 0, y: 0 }], 4)).toBe("M4 99h4v4h-4z");
    expect(gridPath(scale, axis, 112)).toBe("M0 51H112");
  });
});

describe("monthTicks", () => {
  it("labels months whose middle is in range", () => {
    const from = dayNumber("2026-06-01");
    const to = dayNumber("2026-08-10");
    const scale = new Scale({ width: 500, height: 100 }, from, to, { min: 0, max: 1, ticks: [] });
    expect(monthTicks(scale, from, to, (m) => m).map((t) => t.label)).toEqual(["2026-06", "2026-07"]);
  });

  it("thins out labels, keeping the newest", () => {
    const from = dayNumber("2024-01-01");
    const to = dayNumber("2026-12-31");
    const scale = new Scale({ width: 500, height: 100 }, from, to, { min: 0, max: 1, ticks: [] });
    const labels = monthTicks(scale, from, to, (m) => m, 6).map((t) => t.label);
    expect(labels.length).toBeLessThanOrEqual(6);
    expect(labels[labels.length - 1]).toBe("2026-12");
  });
});
