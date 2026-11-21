// What the HISTORY view shows, from the pulled rows (decision 110).

import type { Dataset } from "../data/dataset";
import { dayKey, dayNumber, keyOfDayNumber, monthKey, weekdayMondayFirst } from "./days";
import { newBestsBySession, setOnesByExercise } from "./progress";

export interface Workout {
  id: string;
  routineName: string;
  startedAt: number;
  durationMs: number;
  setCount: number;
  /** Exercises that set a new best on set 1 in this workout. */
  newBests: Set<string>;
  day: string;
}

export interface MonthGroup {
  month: string;
  workouts: Workout[];
}

export interface Panels {
  thisWeek: number;
  thisMonth: number;
  timeThisMonthMs: number;
  bestsThisMonth: number;
}

export interface GridDay {
  day: string;
  trained: boolean;
  isToday: boolean;
  isFuture: boolean;
}

/** Finished workouts, newest first. A workout still in progress is not history yet. */
export function finishedWorkouts(dataset: Dataset, timeZone: string): Workout[] {
  const setCounts = new Map<string, number>();
  for (const entry of dataset.setEntries.values()) setCounts.set(entry.sessionId, (setCounts.get(entry.sessionId) ?? 0) + 1);
  const bests = newBestsBySession(setOnesByExercise(dataset));
  const workouts: Workout[] = [];
  for (const s of dataset.sessions.values()) {
    if (s.endedAt == null) continue;
    workouts.push({
      id: s.id,
      routineName: dataset.routines.get(s.routineId)?.name ?? "",
      startedAt: s.startedAt,
      durationMs: Math.max(0, s.endedAt - s.startedAt),
      setCount: setCounts.get(s.id) ?? 0,
      newBests: bests.get(s.id) ?? new Set(),
      day: dayKey(s.startedAt, timeZone),
    });
  }
  return workouts.sort((a, b) => b.startedAt - a.startedAt);
}

/** Workouts grouped by calendar month, newest month first; [workouts] must be newest first. */
export function byMonth(workouts: Workout[]): MonthGroup[] {
  const groups: MonthGroup[] = [];
  for (const w of workouts) {
    const month = monthKey(w.day);
    const last = groups[groups.length - 1];
    if (last && last.month === month) last.workouts.push(w);
    else groups.push({ month, workouts: [w] });
  }
  return groups;
}

/** The four stat panels; weeks start on Monday. */
export function panels(workouts: Workout[], now: number, timeZone: string): Panels {
  const today = dayKey(now, timeZone);
  const weekStart = keyOfDayNumber(dayNumber(today) - weekdayMondayFirst(today));
  const month = monthKey(today);
  const result: Panels = { thisWeek: 0, thisMonth: 0, timeThisMonthMs: 0, bestsThisMonth: 0 };
  for (const w of workouts) {
    if (w.day >= weekStart && w.day <= today) result.thisWeek++;
    if (monthKey(w.day) === month) {
      result.thisMonth++;
      result.timeThisMonthMs += w.durationMs;
      result.bestsThisMonth += w.newBests.size;
    }
  }
  return result;
}

/** The last [weeks] weeks, Monday to Sunday, oldest first, ending with this week. */
export function dayGrid(workouts: Workout[], now: number, timeZone: string, weeks = 18): GridDay[] {
  const today = dayKey(now, timeZone);
  const todayN = dayNumber(today);
  const firstN = todayN - weekdayMondayFirst(today) - (weeks - 1) * 7;
  const trained = new Set(workouts.map((w) => w.day));
  const days: GridDay[] = [];
  for (let n = firstN; n < firstN + weeks * 7; n++) {
    const day = keyOfDayNumber(n);
    days.push({ day, trained: trained.has(day), isToday: n === todayN, isFuture: n > todayN });
  }
  return days;
}

export interface WorkoutExercise {
  exerciseId: string;
  name: string;
  template: string | null;
  isNewBest: boolean;
  sets: { setIndex: number; reps: number; weightKg: number | null; note: string | null }[];
}

/**
 * One workout's exercises in the routine's order (exercises no longer in the routine
 * follow, by first logged set), each with its sets by set number.
 */
export function workoutExercises(dataset: Dataset, workout: Workout): WorkoutExercise[] {
  const session = dataset.sessions.get(workout.id);
  if (!session) return [];
  const order = new Map<string, number>();
  for (const re of dataset.routineExercises.values()) if (re.routineId === session.routineId) order.set(re.exerciseId, re.position);
  const byExercise = new Map<string, WorkoutExercise>();
  const entries = [...dataset.setEntries.values()].filter((e) => e.sessionId === workout.id).sort((a, b) => a.updatedAt - b.updatedAt);
  for (const e of entries) {
    let ex = byExercise.get(e.exerciseId);
    if (!ex) {
      const exercise = dataset.exercises.get(e.exerciseId);
      ex = {
        exerciseId: e.exerciseId,
        name: exercise?.name ?? "",
        template: exercise?.kind === "TEMPLATE" ? (exercise.template ?? null) : null,
        isNewBest: workout.newBests.has(e.exerciseId),
        sets: [],
      };
      byExercise.set(e.exerciseId, ex);
    }
    ex.sets.push({ setIndex: e.setIndex, reps: e.reps, weightKg: e.weightKg ?? null, note: e.note ?? null });
  }
  const firstSeen = [...byExercise.keys()];
  const rank = (id: string) => order.get(id) ?? Number.MAX_SAFE_INTEGER;
  return [...byExercise.values()]
    .map((ex) => ({ ...ex, sets: ex.sets.sort((a, b) => a.setIndex - b.setIndex) }))
    .sort((a, b) => rank(a.exerciseId) - rank(b.exerciseId) || firstSeen.indexOf(a.exerciseId) - firstSeen.indexOf(b.exerciseId));
}
