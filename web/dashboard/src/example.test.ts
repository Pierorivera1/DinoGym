import { describe, expect, it } from "vitest";
import { exampleDataset } from "./example";
import { finishedWorkouts } from "./rules/history";
import { buildModel } from "./ui/model";

const NOW = Date.parse("2026-11-21T12:00:00Z");

describe("example account", () => {
  it("covers about two months, four workouts a week at most", () => {
    const ds = exampleDataset(false, NOW);
    const workouts = finishedWorkouts(ds, "UTC");
    expect(workouts.length).toBeGreaterThan(20);
    expect(workouts.length).toBeLessThanOrEqual(36);
    const oldest = workouts[workouts.length - 1]!.startedAt;
    expect(NOW - oldest).toBeLessThanOrEqual(61 * 86_400_000);
    expect(workouts.every((w) => w.startedAt < NOW)).toBe(true);
    expect(ds.weighIns.size).toBeGreaterThan(15);
    expect(ds.weighIns.size).toBeLessThanOrEqual(30);
  });

  it("is the same on every visit and in the visitor's language", () => {
    const a = buildModel(exampleDataset(false, NOW), "UTC");
    const b = buildModel(exampleDataset(false, NOW), "UTC");
    expect(a.workouts.map((w) => w.setCount)).toEqual(b.workouts.map((w) => w.setCount));
    expect(a.exercises.some((e) => e.name === "Pull-ups")).toBe(true);
    const es = buildModel(exampleDataset(true, NOW), "UTC");
    expect(es.exercises.some((e) => e.name === "Dominadas")).toBe(true);
    expect(es.goalKg).toBe(74);
  });

  it("has records to show on PROGRESS", () => {
    const model = buildModel(exampleDataset(false, NOW), "UTC");
    expect(model.workouts.some((w) => w.newBests.size > 0)).toBe(true);
  });
});
