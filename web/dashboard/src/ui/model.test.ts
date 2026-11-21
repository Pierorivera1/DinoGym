import { describe, expect, it } from "vitest";
import { applyChanges } from "../data/dataset";
import { dayKey, dayNumber } from "../rules/days";
import { D, dataset, workout } from "../test/fixtures";
import { buildModel, displayName, progressInRange, rangeStart, weightInRange } from "./model";

const UTC = "UTC";
const day = (key: string) => Date.parse(`${key}T10:00:00Z`);

function pullUps() {
  const ds = dataset();
  workout(ds, "pull", day("2026-01-05"), [["pu", [4]]]);
  workout(ds, "pull", day("2026-03-02"), [["pu", [5]]]);
  workout(ds, "pull", day("2026-09-14"), [["pu", [5, 4]], ["row", [10]]]);
  workout(ds, "pull", day("2026-11-16"), [["pu", [7, 6, 5]], ["row", [10]]]);
  return ds;
}

describe("buildModel", () => {
  it("lists trained exercises, most recent first, with slugs and routines", () => {
    const model = buildModel(pullUps(), UTC);
    expect(model.exercises.map((e) => e.slug)).toEqual(expect.arrayContaining(["pull-ups", "rows"]));
    const pu = model.exercises.find((e) => e.slug === "pull-ups")!;
    expect(pu.routines).toEqual(["PULL"]);
    expect(pu.progress.best?.reps).toBe(7);
    expect(model.workoutKey.size).toBe(4);
  });

  it("keeps the goal from the synced preference", () => {
    const ds = dataset();
    applyChanges(ds, { preferences: [{ key: "body_weight_goal_kg", value: "70.0", updatedAt: 1 }] });
    expect(buildModel(ds, UTC).goalKg).toBe(70);
  });
});

describe("progressInRange", () => {
  const model = buildModel(pullUps(), UTC);
  const pu = model.exercises.find((e) => e.slug === "pull-ups")!;
  const today = dayNumber("2026-11-20");

  it("shows everything for ALL, gaining from the first record", () => {
    const r = progressInRange(pu, "ALL", today, UTC);
    expect(r.steps.map((s) => s.value)).toEqual([4, 5, 7]);
    expect(r.sessions).toHaveLength(4);
    expect(r.lead).toBeNull();
    expect(r.gain).toEqual({ kind: "reps", reps: 3 });
    expect(r.gainSince).toBe("2026-01-05");
    expect(r.records.map((x) => x.gain)).toEqual([{ kind: "reps", reps: 2 }, { kind: "reps", reps: 1 }, null]);
  });

  it("starts a shorter range at the best it began with", () => {
    const r = progressInRange(pu, "3M", today, UTC);
    expect(r.lead).toEqual({ x: rangeStart("3M", today), value: 5 });
    expect(r.steps.map((s) => s.value)).toEqual([7]);
    expect(r.sessions.map((s) => s.value)).toEqual([5, 7]);
    expect(r.gain).toEqual({ kind: "reps", reps: 2 });
    expect(r.gainSince).toBe("2026-03-02");
    expect(r.records).toHaveLength(1);
  });
});

describe("weightInRange", () => {
  it("averages and changes within the range", () => {
    const ds = dataset();
    const base = day("2026-10-01");
    applyChanges(ds, {
      weighIns: [0, 1, 2, 40, 41].map((d, i) => ({ id: `w${i}`, measuredAt: base + d * D, weightKg: 74 - i * 0.5, updatedAt: 1 })),
    });
    const model = buildModel(ds, UTC);
    const today = dayNumber(dayKey(base + 45 * D, UTC));
    const all = weightInRange(model, "ALL", today, UTC);
    expect(all.raw.map((p) => p.value)).toEqual([74, 73.5, 73, 72.5, 72]);
    expect(all.change).toBeCloseTo(72.25 - 74);
    const month = weightInRange(model, "1M", today, UTC);
    expect(month.raw).toHaveLength(2);
    expect(month.change).toBeCloseTo(72.25 - 72.5);
  });
});

describe("displayName", () => {
  const token = (claims: object) => `x.${btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(claims)))).replace(/=+$/, "")}.y`;

  it("shortens the family name", () => {
    expect(displayName(token({ given_name: "Alex", family_name: "Martínez" }))).toBe("Alex M.");
  });

  it("falls back to the name or nothing", () => {
    expect(displayName(token({ name: "Alex" }))).toBe("Alex");
    expect(displayName("garbage")).toBe("");
  });
});
