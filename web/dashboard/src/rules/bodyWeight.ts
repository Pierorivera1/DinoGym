// Body weight rules, ported from the app's BodyWeightRules (decisions 32 and 49).

import type { WeighIn } from "../api/types";
import { dayKey, dayNumber } from "./days";

export const AVERAGE_DAYS = 7;
const REACHED_KG = 0.05;

export interface DailyAverage {
  day: string;
  averageKg: number;
}

export type GoalDirection = "lose" | "gain" | "reached";

export interface GoalProgress {
  direction: GoalDirection;
  remainingKg: number;
}

/**
 * One average per day with a weigh-in, oldest first: the mean of every weigh-in on that
 * day and the 6 days before it. Several weigh-ins on one day all count.
 */
export function trailingAverages(weighIns: Iterable<WeighIn>, timeZone: string): DailyAverage[] {
  const byDay = new Map<string, number[]>();
  for (const w of weighIns) {
    const key = dayKey(w.measuredAt, timeZone);
    const list = byDay.get(key);
    if (list) list.push(w.weightKg);
    else byDay.set(key, [w.weightKg]);
  }
  const days = [...byDay.keys()].sort();
  return days.map((day) => {
    const to = dayNumber(day);
    const window = days.filter((d) => dayNumber(d) > to - AVERAGE_DAYS && dayNumber(d) <= to).flatMap((d) => byDay.get(d)!);
    return { day, averageKg: window.reduce((a, b) => a + b, 0) / window.length };
  });
}

/** CURRENT: the average on the latest day with a weigh-in, so a gap never empties it. */
export function currentAverage(averages: DailyAverage[]): number | null {
  return averages[averages.length - 1]?.averageKg ?? null;
}

/** CHANGE: latest average minus the first on or after [startDay] (all when null). Null under two days. */
export function change(averages: DailyAverage[], startDay: string | null): number | null {
  const inRange = startDay == null ? averages : averages.filter((a) => a.day >= startDay);
  if (inRange.length < 2) return null;
  return inRange[inRange.length - 1]!.averageKg - inRange[0]!.averageKg;
}

export function roundTenth(kg: number): number {
  return Math.round(kg * 10) / 10;
}

export function goalProgress(currentKg: number, goalKg: number): GoalProgress {
  const diff = goalKg - currentKg;
  const direction: GoalDirection = Math.abs(diff) < REACHED_KG ? "reached" : diff < 0 ? "lose" : "gain";
  return { direction, remainingKg: direction === "reached" ? 0 : roundTenth(Math.abs(diff)) };
}

/** True when the change moved toward the goal; it is coloured as progress. */
export function isTowardGoal(changeKg: number, direction: GoalDirection): boolean {
  return direction === "lose" ? changeKg < 0 : direction === "gain" ? changeKg > 0 : false;
}

/** The goal preference (decision 49), or null when unset or unreadable. */
export function goalKg(value: string | undefined): number | null {
  if (value == null) return null;
  const n = Number(value.replace(",", "."));
  return Number.isFinite(n) ? n : null;
}
