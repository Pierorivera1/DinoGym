// What the three views show, computed once per load from the pulled rows with the
// ported rules (rules/). The views only lay it out.

import type { WeighIn } from "../api/types";
import type { Dataset } from "../data/dataset";
import { trailingAverages, goalKg, type DailyAverage } from "../rules/bodyWeight";
import { dayKey, dayNumber } from "../rules/days";
import { finishedWorkouts, type Workout } from "../rules/history";
import { gain, progress, setOnesByExercise, type ExerciseProgress, type Gain, type SetOneResult } from "../rules/progress";
import { uniqueSlugs, workoutKeys } from "../routes";

export interface ProgressExercise {
  id: string;
  slug: string;
  name: string;
  iconKey: string;
  /** Routine names that include the exercise, for the subtitle. */
  routines: string[];
  history: SetOneResult[];
  progress: ExerciseProgress;
  lastTrained: number;
}

export interface Model {
  workouts: Workout[];
  /** Workout id → URL key (routes.ts). */
  workoutKey: Map<string, string>;
  exercises: ProgressExercise[];
  /** Exercise id → slug, for links from a workout. */
  exerciseSlug: Map<string, string>;
  weighIns: WeighIn[];
  averages: DailyAverage[];
  goalKg: number | null;
  routineNameBySession: Map<string, string>;
}

const GOAL_KEY = "body_weight_goal_kg";

export function buildModel(dataset: Dataset, timeZone: string): Model {
  const workouts = finishedWorkouts(dataset, timeZone);
  const routineNameBySession = new Map(workouts.map((w) => [w.id, w.routineName]));

  const routinesByExercise = new Map<string, Set<string>>();
  for (const re of dataset.routineExercises.values()) {
    const name = dataset.routines.get(re.routineId)?.name;
    if (!name) continue;
    let set = routinesByExercise.get(re.exerciseId);
    if (!set) routinesByExercise.set(re.exerciseId, (set = new Set()));
    set.add(name);
  }

  const histories = setOnesByExercise(dataset);
  const unsorted: Omit<ProgressExercise, "slug">[] = [];
  for (const [id, history] of histories) {
    if (history.length === 0) continue;
    const exercise = dataset.exercises.get(id);
    unsorted.push({
      id,
      name: exercise?.name ?? "",
      iconKey: exercise?.iconKey ?? "GENERIC",
      routines: [...(routinesByExercise.get(id) ?? [])],
      history,
      progress: progress(history),
      lastTrained: history[history.length - 1]!.date,
    });
  }
  // Slugs go to the exercise trained first, so a link keeps working as more are added.
  const slugs = uniqueSlugs([...unsorted].sort((a, b) => a.history[0]!.date - b.history[0]!.date || a.id.localeCompare(b.id)));
  const exercises = unsorted
    .map((e) => ({ ...e, slug: slugs.get(e.id)! }))
    .sort((a, b) => b.lastTrained - a.lastTrained || a.name.localeCompare(b.name));

  const weighIns = [...dataset.weighIns.values()].sort((a, b) => b.measuredAt - a.measuredAt);
  return {
    workouts,
    workoutKey: workoutKeys(workouts),
    exercises,
    exerciseSlug: slugs,
    weighIns,
    averages: trailingAverages(weighIns, timeZone),
    goalKg: goalKg(dataset.preferences.get(GOAL_KEY)?.value),
    routineNameBySession,
  };
}

/** Range ids are the English labels; each view offers four of them. */
export type RangeId = "1M" | "3M" | "6M" | "1Y" | "ALL";

const RANGE_DAYS: Record<RangeId, number | null> = { "1M": 30, "3M": 91, "6M": 182, "1Y": 365, ALL: null };

/** The first day number inside [range], or null for ALL. */
export function rangeStart(range: RangeId, todayN: number): number | null {
  const days = RANGE_DAYS[range];
  return days == null ? null : todayN - days;
}

export interface ProgressRecordRow {
  result: SetOneResult;
  day: string;
  /** Null for the exercise's first record ever. */
  gain: Gain | null;
}

export interface ProgressInRange {
  /** Every workout's set 1 in the range: the chart's dots. */
  sessions: { x: number; value: number }[];
  /** Records in the range, oldest first: the chart's steps. */
  steps: { x: number; value: number }[];
  /** The best when the range began, drawn flat from the left edge. */
  lead: { x: number; value: number } | null;
  /** Records in the range, newest first, for the list. */
  records: ProgressRecordRow[];
  /** From the best at the start of the range (or the first record in it) to the best now. */
  gain: Gain;
  /** The day the gain is counted from. */
  gainSince: string | null;
  xMin: number;
  xMax: number;
}

export function valueOf(result: SetOneResult, usesWeight: boolean): number {
  return usesWeight ? (result.weightKg ?? 0) : result.reps;
}

export function progressInRange(exercise: ProgressExercise, range: RangeId, todayN: number, timeZone: string): ProgressInRange {
  const { history, progress: p } = exercise;
  const start = rangeStart(range, todayN);
  const dayN = (r: SetOneResult) => dayNumber(dayKey(r.date, timeZone));
  const inRange = (r: SetOneResult) => start == null || dayN(r) >= start;
  const usesWeight = p.usesWeight;

  const before = p.records.filter((r) => !inRange(r));
  const prior = before[before.length - 1] ?? null;
  const recordsIn = p.records.filter(inRange);
  const firstX = start ?? (history[0] ? dayN(history[0]) : todayN);
  const xMin = Math.min(firstX, todayN - 1);

  const from = prior ?? recordsIn[0] ?? null;
  const best = p.best;
  const records: ProgressRecordRow[] = p.recordsNewestFirst
    .filter((r) => inRange(r.result))
    .map((r) => ({ result: r.result, day: dayKey(r.result.date, timeZone), gain: r.gain }));

  return {
    sessions: history.filter(inRange).map((r) => ({ x: dayN(r), value: valueOf(r, usesWeight) })),
    steps: recordsIn.map((r) => ({ x: dayN(r), value: valueOf(r, usesWeight) })),
    lead: prior && start != null ? { x: start, value: valueOf(prior, usesWeight) } : null,
    records,
    gain: from && best ? gain(from, best) : { kind: "reps", reps: 0 },
    gainSince: from ? dayKey(from.date, timeZone) : null,
    xMin,
    xMax: todayN,
  };
}

export interface WeightInRange {
  raw: { x: number; value: number }[];
  averages: { x: number; value: number }[];
  /** Latest average minus the first average in the range; null under two days. */
  change: number | null;
  xMin: number;
  xMax: number;
}

export function weightInRange(model: Model, range: RangeId, todayN: number, timeZone: string): WeightInRange {
  const start = rangeStart(range, todayN);
  const raw = model.weighIns
    .map((w) => ({ x: dayNumber(dayKey(w.measuredAt, timeZone)), value: w.weightKg }))
    .filter((p) => start == null || p.x >= start)
    .reverse();
  const averages = model.averages.map((a) => ({ x: dayNumber(a.day), value: a.averageKg })).filter((p) => start == null || p.x >= start);
  const first = averages[0];
  const last = averages[averages.length - 1];
  return {
    raw,
    averages,
    change: first && last && averages.length >= 2 ? last.value - first.value : null,
    xMin: start ?? Math.min(raw[0]?.x ?? todayN - 30, todayN - 1),
    xMax: todayN,
  };
}

/** "Alex M." from a Google ID token's claims; the token is only read for display. */
export function displayName(idToken: string): string {
  try {
    const payload = idToken.split(".")[1] ?? "";
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
        .split("")
        .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join(""),
    );
    const claims = JSON.parse(json) as { given_name?: string; family_name?: string; name?: string; email?: string };
    if (claims.given_name) return claims.family_name ? `${claims.given_name} ${claims.family_name.charAt(0)}.` : claims.given_name;
    return claims.name ?? claims.email ?? "";
  } catch {
    return "";
  }
}
