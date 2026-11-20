// Ported from ProgressRulesTest and SetOneBestsTest (Android), so both sides apply the same rules.
import { describe, expect, it } from "vitest";
import { bests, isBetter, newBestsBySession, progress, type SetOneResult } from "./progress";

const r = (day: number, reps: number, weightKg: number | null = null, session = `s${day}`): SetOneResult => ({
  sessionId: session,
  date: day,
  reps,
  weightKg,
});

describe("set 1 bests", () => {
  it("keeps only days with more reps without weight", () => {
    expect(bests([r(1, 4), r(2, 4), r(3, 5), r(4, 3), r(5, 7)]).map((x) => x.date)).toEqual([1, 3, 5]);
  });
  it("counts more weight as better even with fewer reps", () => {
    expect(isBetter(r(2, 6, 22.5), r(1, 10, 20))).toBe(true);
    expect(isBetter(r(2, 12, 17.5), r(1, 8, 20))).toBe(false);
  });
  it("treats a missing weight as 0 kg", () => {
    expect(isBetter(r(2, 5, 0), r(1, 5, null))).toBe(false);
    expect(isBetter(r(2, 1, 2.5), r(1, 20, null))).toBe(true);
  });
  it("has no bests without history", () => {
    expect(bests([])).toEqual([]);
  });
});

describe("progress", () => {
  it("has no best and no gain without history", () => {
    const p = progress([]);
    expect(p.best).toBeNull();
    expect(p.gain).toEqual({ kind: "reps", reps: 0 });
    expect(p.usesWeight).toBe(false);
  });
  it("gains reps over the first record without weight", () => {
    const p = progress([r(1, 4), r(2, 4), r(3, 5), r(4, 3), r(5, 7)]);
    expect(p.best?.reps).toBe(7);
    expect(p.gain).toEqual({ kind: "reps", reps: 3 });
  });
  it("lists records newest first with each gain and FIRST last", () => {
    const list = progress([r(1, 4), r(2, 5), r(3, 7)]).recordsNewestFirst;
    expect(list.map((x) => x.result.date)).toEqual([3, 2, 1]);
    expect(list.map((x) => x.gain)).toEqual([{ kind: "reps", reps: 2 }, { kind: "reps", reps: 1 }, null]);
  });
  it("shows a kg gain when the weight went up", () => {
    const p = progress([r(1, 8, 20), r(2, 10, 20), r(3, 6, 22.5), r(4, 9, 20)]);
    expect(p.usesWeight).toBe(true);
    expect(p.records.map((x) => x.date)).toEqual([1, 2, 3]);
    expect(p.gain).toEqual({ kind: "kg", kg: 2.5 });
    expect(p.recordsNewestFirst.map((x) => x.gain)).toEqual([{ kind: "kg", kg: 2.5 }, { kind: "reps", reps: 2 }, null]);
  });
  it("does not count 0 kg as weight", () => {
    expect(progress([r(1, 8, 0), r(2, 9, 0)]).usesWeight).toBe(false);
  });
  it("skips each exercise's first record when tagging sessions", () => {
    const byExercise = new Map([
      ["pull", [r(1, 4, null, "a"), r(2, 5, null, "b"), r(3, 5, null, "c")]],
      ["dip", [r(1, 8, null, "a"), r(3, 9, null, "c")]],
    ]);
    expect(newBestsBySession(byExercise)).toEqual(
      new Map([
        ["b", new Set(["pull"])],
        ["c", new Set(["dip"])],
      ]),
    );
  });
});
