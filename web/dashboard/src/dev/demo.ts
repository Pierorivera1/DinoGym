// A made-up account for looking at the views without signing in: `npm run dev`, then
// http://localhost:5173/?demo. Only loaded when import.meta.env.DEV is true, so a
// production build does not contain it.

import type { Exercise, Routine, RoutineExercise, SetEntry, WeighIn, WorkoutSession } from "../api/types";
import { applyChanges, emptyDataset, type Dataset } from "../data/dataset";

const DAY = 86_400_000;

// A small deterministic random, so every load looks the same.
function random(seed: number): () => number {
  let s = seed;
  return () => {
    s = (s * 1_103_515_245 + 12_345) % 2_147_483_648;
    return s / 2_147_483_648;
  };
}

const EXERCISES: [id: string, en: string, es: string, icon: string, weight: boolean, template?: string][] = [
  ["fl", "Front lever + pulls", "Front lever + dominadas", "LEVER", false, "3s + {n} pulls + 3s"],
  ["pu", "Pull-ups", "Dominadas", "PULL_UP", false],
  ["row", "Rows", "Remo", "ROW", false],
  ["bc", "Bicep curl", "Curl de bíceps", "CURL", true],
  ["dip", "Dips", "Fondos", "DIP", false],
  ["push", "Push-ups", "Flexiones", "PUSH_UP", false],
  ["sq", "Squat", "Sentadilla", "SQUAT", true],
  ["rc", "Reverse curl", "Curl inverso", "CURL", false],
];

const ROUTINES: [id: string, name: string, exercises: string[]][] = [
  ["pull", "PULL", ["fl", "pu", "row", "bc", "rc"]],
  ["push", "PUSH", ["dip", "push"]],
  ["legs", "LEGS", ["sq"]],
];

export function demoDataset(spanish: boolean, now = Date.now()): Dataset {
  const rnd = random(7);
  const ds = emptyDataset();
  const exercises: Exercise[] = EXERCISES.map(([id, en, es, icon, weight, template]) => ({
    id,
    name: spanish ? es : en,
    iconKey: icon,
    kind: template ? "TEMPLATE" : "REPS",
    template: template ?? null,
    weightEnabled: weight,
    archived: false,
    updatedAt: 0,
  }));
  const routines: Routine[] = ROUTINES.map(([id, name]) => ({ id, name, updatedAt: 0 }));
  const routineExercises: RoutineExercise[] = ROUTINES.flatMap(([rid, , ids]) =>
    ids.map((eid, i) => ({ id: `${rid}-${eid}`, routineId: rid, exerciseId: eid, position: i, sets: 3, repMin: 5, repMax: 12, restSeconds: 90, updatedAt: 0 })),
  );

  const start = Number(new Date(now - 200 * DAY).setHours(18, 0, 0, 0));
  const sessions: WorkoutSession[] = [];
  const entries: SetEntry[] = [];
  const level: Record<string, number> = { fl: 2, pu: 4, row: 8, bc: 10, dip: 8, push: 18, sq: 6, rc: 9 };
  const kgs: Record<string, number> = { bc: 10, sq: 50 };
  let n = 0;
  for (let d = 0; d < 200; d++) {
    if ([2, 5, 6].includes(d % 7) || rnd() < 0.12) continue;
    const [rid, , ids] = ROUTINES[n++ % ROUTINES.length]!;
    const startedAt = start + d * DAY + Math.floor(rnd() * 3_600_000);
    const id = `s${d}`;
    sessions.push({ id, routineId: rid, programId: "p", startedAt, endedAt: startedAt + (28 + Math.floor(rnd() * 22)) * 60_000 + Math.floor(rnd() * 59_000), currentPosition: 0, updatedAt: startedAt });
    ids.forEach((eid, i) => {
      if (rnd() < 0.18) level[eid] = level[eid]! + 1;
      if (kgs[eid] != null && rnd() < 0.08) kgs[eid] = kgs[eid]! + 2.5;
      const first = level[eid]! - (rnd() < 0.25 ? 1 : 0);
      for (let set = 1; set <= 3; set++) {
        entries.push({
          id: `${id}-${eid}-${set}`,
          sessionId: id,
          exerciseId: eid,
          setIndex: set,
          reps: Math.max(1, first - (set - 1)),
          weightKg: kgs[eid] ?? null,
          updatedAt: startedAt + i * 1000 + set,
        });
      }
    });
  }

  const weighIns: WeighIn[] = [];
  for (let d = 110; d < 200; d++) {
    if (d % 7 === 2 || d % 7 === 5) continue;
    const trend = 74 - (1.6 * (d - 110)) / 90;
    const noise = 0.35 * Math.sin(d * 1.7) + 0.2 * Math.sin(d * 0.6 + 1);
    weighIns.push({ id: `w${d}`, measuredAt: start + d * DAY - 10 * 3_600_000, weightKg: Math.round((trend + noise) * 10) / 10, updatedAt: 0 });
  }

  applyChanges(ds, {
    exercises,
    routines,
    routineExercises,
    sessions,
    setEntries: entries,
    weighIns,
    preferences: [{ key: "body_weight_goal_kg", value: "70.0", updatedAt: 0 }],
  });
  return ds;
}
