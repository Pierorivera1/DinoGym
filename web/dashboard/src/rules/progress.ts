// Progress rules, ported from the app's SetOneBests and ProgressRules (decisions 28 and 47).
// Only set 1 counts; a record is a day set 1 beat the previous best.

import type { Dataset } from "../data/dataset";

export interface SetOneResult {
  sessionId: string;
  date: number;
  reps: number;
  weightKg: number | null;
}

export type Gain = { kind: "reps"; reps: number } | { kind: "kg"; kg: number };

export interface ProgressRecord {
  result: SetOneResult;
  /** Null for the first record (`FIRST`). */
  gain: Gain | null;
}

export interface ExerciseProgress {
  best: SetOneResult | null;
  gain: Gain;
  /** Oldest first: the chart's steps. */
  records: SetOneResult[];
  usesWeight: boolean;
  recordsNewestFirst: ProgressRecord[];
}

/** More weight (a missing weight is 0 kg), or the same weight with more reps. */
export function isBetter(candidate: SetOneResult, best: SetOneResult): boolean {
  const candidateKg = candidate.weightKg ?? 0;
  const bestKg = best.weightKg ?? 0;
  return candidateKg !== bestKg ? candidateKg > bestKg : candidate.reps > best.reps;
}

/** The results that beat every earlier one, from a history oldest first. The first is always kept. */
export function bests(history: SetOneResult[]): SetOneResult[] {
  const records: SetOneResult[] = [];
  for (const result of history) {
    const best = records[records.length - 1];
    if (!best || isBetter(result, best)) records.push(result);
  }
  return records;
}

/** Kilograms when the weight went up, otherwise reps. */
export function gain(from: SetOneResult, to: SetOneResult): Gain {
  const kg = (to.weightKg ?? 0) - (from.weightKg ?? 0);
  return kg > 0 ? { kind: "kg", kg } : { kind: "reps", reps: to.reps - from.reps };
}

export function progress(history: SetOneResult[]): ExerciseProgress {
  const records = bests(history);
  const first = records[0];
  const best = records[records.length - 1] ?? null;
  return {
    best,
    gain: first && best ? gain(first, best) : { kind: "reps", reps: 0 },
    records,
    usesWeight: history.some((r) => (r.weightKg ?? 0) > 0),
    recordsNewestFirst: records.map((r, i) => ({ result: r, gain: i === 0 ? null : gain(records[i - 1]!, r) })).reverse(),
  };
}

/** Session id → exercise ids that set a new best there. An exercise's first record is not a new best. */
export function newBestsBySession(historyByExercise: Map<string, SetOneResult[]>): Map<string, Set<string>> {
  const result = new Map<string, Set<string>>();
  for (const [exerciseId, history] of historyByExercise) {
    for (const record of bests(history).slice(1)) {
      let set = result.get(record.sessionId);
      if (!set) result.set(record.sessionId, (set = new Set()));
      set.add(exerciseId);
    }
  }
  return result;
}

/**
 * Set 1 of every exercise in every finished workout, grouped by exercise id, oldest first:
 * the app's `observeAllSetOnes` query over the pulled rows.
 */
export function setOnesByExercise(dataset: Dataset): Map<string, SetOneResult[]> {
  const result = new Map<string, SetOneResult[]>();
  for (const entry of dataset.setEntries.values()) {
    if (entry.setIndex !== 1) continue;
    const session = dataset.sessions.get(entry.sessionId);
    if (!session || session.endedAt == null) continue;
    let list = result.get(entry.exerciseId);
    if (!list) result.set(entry.exerciseId, (list = []));
    list.push({ sessionId: session.id, date: session.startedAt, reps: entry.reps, weightKg: entry.weightKg ?? null });
  }
  for (const list of result.values()) list.sort((a, b) => a.date - b.date);
  return result;
}
