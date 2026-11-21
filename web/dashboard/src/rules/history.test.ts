import { describe, expect, it } from "vitest";
import { applyChanges } from "../data/dataset";
import { D, H, dataset, workout } from "../test/fixtures";
import { byMonth, dayGrid, finishedWorkouts, panels, workoutExercises } from "./history";

// Monday 16 November 2026, 18:00 UTC.
const NOW = Date.UTC(2026, 10, 16, 18);

describe("history", () => {
  it("lists finished workouts newest first with sets, duration and new bests", () => {
    const ds = dataset();
    workout(ds, "pull", NOW - 7 * D, [["pu", [5, 4]]]);
    const later = workout(ds, "pull", NOW - 2 * H, [["pu", [6, 5]], ["row", [10]]], 42);
    applyChanges(ds, { sessions: [{ id: "open", routineId: "push", programId: "p", startedAt: NOW, currentPosition: 0, updatedAt: NOW }] });

    const list = finishedWorkouts(ds, "UTC");
    expect(list.map((w) => w.id)).toEqual([later.id, list[1]!.id]);
    expect(list[0]).toMatchObject({ routineName: "PULL", setCount: 3, durationMs: 42 * 60_000, day: "2026-11-16" });
    // Pull-ups beat 5 with 6; rows have only a first record, which is not a new best.
    expect([...list[0]!.newBests]).toEqual(["pu"]);
    expect(list[1]!.newBests.size).toBe(0);
  });

  it("drops deleted workouts", () => {
    const ds = dataset();
    const s = workout(ds, "pull", NOW - H, [["pu", [5]]]);
    applyChanges(ds, { sessions: [{ ...s, deletedAt: NOW, updatedAt: NOW }] });
    expect(finishedWorkouts(ds, "UTC")).toEqual([]);
  });

  it("groups by month and counts the panels", () => {
    const ds = dataset();
    workout(ds, "pull", Date.UTC(2026, 9, 30, 10), [["pu", [4]]]); // Fri 30 Oct
    workout(ds, "push", Date.UTC(2026, 10, 13, 10), [["row", [9]]], 30); // Fri 13 Nov, last week
    workout(ds, "pull", Date.UTC(2026, 10, 16, 9), [["pu", [5]]], 45); // Mon 16 Nov
    const list = finishedWorkouts(ds, "UTC");
    expect(byMonth(list).map((g) => [g.month, g.workouts.length])).toEqual([
      ["2026-11", 2],
      ["2026-10", 1],
    ]);
    expect(panels(list, NOW, "UTC")).toEqual({ thisWeek: 1, thisMonth: 2, timeThisMonthMs: 75 * 60_000, bestsThisMonth: 1 });
  });

  it("builds a Monday-first grid ending with this week", () => {
    const ds = dataset();
    workout(ds, "pull", Date.UTC(2026, 10, 16, 9), [["pu", [5]]]);
    const grid = dayGrid(finishedWorkouts(ds, "UTC"), NOW, "UTC", 2);
    expect(grid).toHaveLength(14);
    expect(grid[0]!.day).toBe("2026-11-09");
    expect(grid[7]).toEqual({ day: "2026-11-16", trained: true, isToday: true, isFuture: false });
    expect(grid[13]).toMatchObject({ day: "2026-11-22", isFuture: true });
  });

  it("orders a workout's exercises as in the routine, sets by number", () => {
    const ds = dataset();
    workout(ds, "pull", NOW - H, [["pu", [6, 5]], ["row", [10, 9]]]);
    const w = finishedWorkouts(ds, "UTC")[0]!;
    const exs = workoutExercises(ds, w);
    expect(exs.map((e) => e.name)).toEqual(["Rows", "Pull-ups"]);
    expect(exs[1]!.sets.map((s) => [s.setIndex, s.reps])).toEqual([
      [1, 6],
      [2, 5],
    ]);
  });
});
