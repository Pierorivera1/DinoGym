// Ported from BodyWeightRulesTest (Android).
import { describe, expect, it } from "vitest";
import type { WeighIn } from "../api/types";
import { change, currentAverage, goalKg, goalProgress, isTowardGoal, trailingAverages } from "./bodyWeight";
import { dayKey } from "./days";

const day0 = Date.UTC(2026, 10, 1);
const w = (day: number, kg: number, hour = 8): WeighIn => ({
  id: `${day}-${hour}-${kg}`,
  measuredAt: day0 + day * 86_400_000 + hour * 3_600_000,
  weightKg: kg,
  updatedAt: 0,
});
const avgs = (...list: WeighIn[]) => trailingAverages(list, "UTC");

describe("trailing averages", () => {
  it("averages the day and the six before it", () => {
    const a = avgs(w(0, 80), w(3, 79), w(6, 78), w(7, 77));
    expect(a.map((x) => x.day)).toEqual(["2026-11-01", "2026-11-04", "2026-11-07", "2026-11-08"]);
    expect(a.map((x) => x.averageKg)).toEqual([80, 79.5, 79, 78]);
  });
  it("counts several weigh-ins on one day as one point", () => {
    const a = avgs(w(0, 80, 7), w(0, 81, 21), w(1, 79));
    expect(a.map((x) => x.averageKg)).toEqual([80.5, 80]);
  });
  it("ignores input order", () => {
    expect(avgs(w(0, 80), w(2, 78))).toEqual(avgs(w(2, 78), w(0, 80)));
  });
  it("uses the given time zone for days", () => {
    // 23:30 in Lima is already the next day in UTC.
    const late = Date.UTC(2026, 10, 2, 4, 30);
    expect(dayKey(late, "America/Lima")).toBe("2026-11-01");
    expect(dayKey(late, "UTC")).toBe("2026-11-02");
  });
  it("is empty without weigh-ins", () => {
    expect(avgs()).toEqual([]);
    expect(currentAverage([])).toBeNull();
  });
});

describe("panels", () => {
  it("takes CURRENT from the latest average", () => {
    expect(currentAverage(avgs(w(0, 80), w(1, 78)))).toBe(79);
  });
  it("takes CHANGE as latest minus the first average in range", () => {
    const a = avgs(w(0, 82), w(10, 80), w(20, 79));
    expect(change(a, null)).toBeCloseTo(-3);
    expect(change(a, "2026-11-06")).toBeCloseTo(-1);
    expect(change(a, "2026-11-16")).toBeNull();
  });
  it("infers the goal direction from the current average", () => {
    expect(goalProgress(78.4, 76)).toEqual({ direction: "lose", remainingKg: 2.4 });
    expect(goalProgress(70, 71.5)).toEqual({ direction: "gain", remainingKg: 1.5 });
    expect(goalProgress(76.03, 76)).toEqual({ direction: "reached", remainingKg: 0 });
  });
  it("colours only a change toward the goal", () => {
    expect(isTowardGoal(-0.5, "lose")).toBe(true);
    expect(isTowardGoal(0.5, "lose")).toBe(false);
    expect(isTowardGoal(0.5, "gain")).toBe(true);
    expect(isTowardGoal(-0.5, "reached")).toBe(false);
  });
  it("reads the goal preference", () => {
    expect(goalKg("76.0")).toBe(76);
    expect(goalKg(undefined)).toBeNull();
    expect(goalKg("x")).toBeNull();
  });
});
