// The example account behind SEE AN EXAMPLE on the sign-in page (decision 114): made-up
// rows for someone who has used the app for two months, built in the browser. Nothing is
// sent to the API or stored. The dates follow today, so the example always looks current.

import type { Exercise, Routine, RoutineExercise, SetEntry, WeighIn, WorkoutSession } from "./api/types";
import { applyChanges, emptyDataset, type Dataset } from "./data/dataset";

const DAY = 86_400_000;
const DAYS = 60;

// A small deterministic random, so the example is the same on every visit.
function random(seed: number): () => number {
  let s = seed;
  return () => {
    s = (s * 1_103_515_245 + 12_345) % 2_147_483_648;
    return s / 2_147_483_648;
  };
}

type ExampleExercise = { id: string; en: string; es: string; icon: string; reps: number; kg?: number; template?: [string, string] };

const EXERCISES: ExampleExercise[] = [
  { id: "pu", en: "Pull-ups", es: "Dominadas", icon: "PULL_UP", reps: 5 },
  { id: "row", en: "Rows", es: "Remo", icon: "ROW", reps: 8 },
  { id: "bc", en: "Bicep curl", es: "Curl de bíceps", icon: "CURL", reps: 9, kg: 10 },
  { id: "dip", en: "Dips", es: "Fondos", icon: "DIP", reps: 7 },
  { id: "push", en: "Push-ups", es: "Flexiones", icon: "PUSH_UP", reps: 16 },
  { id: "fl", en: "Front lever + pulls", es: "Front lever + dominadas", icon: "LEVER", reps: 2, template: ["3s + {n} pulls + 3s", "3s + {n} dominadas + 3s"] },
  { id: "sq", en: "Squat", es: "Sentadilla", icon: "SQUAT", reps: 8, kg: 40 },
];

const ROUTINES: { id: string; name: string; exercises: string[] }[] = [
  { id: "pull", name: "PULL", exercises: ["fl", "pu", "row", "bc"] },
  { id: "push", name: "PUSH", exercises: ["dip", "push"] },
  { id: "legs", name: "LEGS", exercises: ["sq"] },
];

export const EXAMPLE_NAME = "Alex M.";

export function exampleDataset(spanish: boolean, now = Date.now()): Dataset {
  const rnd = random(11);
  const exercises: Exercise[] = EXERCISES.map((e) => ({
    id: e.id,
    name: spanish ? e.es : e.en,
    iconKey: e.icon,
    kind: e.template ? "TEMPLATE" : "REPS",
    template: e.template ? e.template[spanish ? 1 : 0] : null,
    weightEnabled: e.kg != null,
    archived: false,
    updatedAt: 0,
  }));
  const routines: Routine[] = ROUTINES.map((r) => ({ id: r.id, name: r.name, updatedAt: 0 }));
  const routineExercises: RoutineExercise[] = ROUTINES.flatMap((r) =>
    r.exercises.map((eid, i) => ({ id: `${r.id}-${eid}`, routineId: r.id, exerciseId: eid, position: i, sets: 3, repMin: 5, repMax: 12, restSeconds: 90, updatedAt: 0 })),
  );

  // Day 0 is two months ago, at 6 pm local time; the last day is yesterday.
  const start = new Date(now - DAYS * DAY).setHours(18, 0, 0, 0);
  const level = new Map(EXERCISES.map((e) => [e.id, e.reps]));
  const kg = new Map(EXERCISES.filter((e) => e.kg != null).map((e) => [e.id, e.kg!]));
  const sessions: WorkoutSession[] = [];
  const entries: SetEntry[] = [];
  let next = 0;
  for (let d = 0; d < DAYS; d++) {
    // Four days a week, with the odd missed day.
    if ([2, 5, 6].includes(d % 7) || rnd() < 0.1) continue;
    const routine = ROUTINES[next++ % ROUTINES.length]!;
    const startedAt = start + d * DAY + Math.floor(rnd() * 2 * 3_600_000) - 3_600_000;
    const id = `example-${d}`;
    const minutes = routine.id === "legs" ? 25 + rnd() * 10 : 35 + rnd() * 15;
    sessions.push({ id, routineId: routine.id, programId: "example", startedAt, endedAt: startedAt + Math.floor(minutes * 60_000), currentPosition: 0, updatedAt: startedAt });
    routine.exercises.forEach((eid, i) => {
      if (rnd() < 0.22) level.set(eid, level.get(eid)! + 1);
      if (kg.has(eid) && rnd() < 0.12) kg.set(eid, kg.get(eid)! + 2.5);
      const first = level.get(eid)! - (rnd() < 0.3 ? 1 : 0);
      for (let set = 1; set <= 3; set++) {
        entries.push({
          id: `${id}-${eid}-${set}`,
          sessionId: id,
          exerciseId: eid,
          setIndex: set,
          reps: Math.max(1, first - (set - 1) - (rnd() < 0.3 ? 1 : 0)),
          weightKg: kg.get(eid) ?? null,
          updatedAt: startedAt + i * 1000 + set,
        });
      }
    });
  }

  // Three weigh-ins a week, drifting from 76 kg toward the 74 kg goal.
  const weighIns: WeighIn[] = [];
  for (let d = 0; d < DAYS; d++) {
    if (![0, 2, 4].includes(d % 7)) continue;
    const trend = 76 - (1.4 * d) / DAYS;
    const noise = 0.3 * Math.sin(d * 1.7) + 0.15 * Math.sin(d * 0.6 + 1);
    weighIns.push({ id: `example-w${d}`, measuredAt: start + d * DAY - 11 * 3_600_000, weightKg: Math.round((trend + noise) * 10) / 10, updatedAt: 0 });
  }

  const dataset = emptyDataset();
  applyChanges(dataset, {
    exercises,
    routines,
    routineExercises,
    sessions,
    setEntries: entries,
    weighIns,
    preferences: [{ key: "body_weight_goal_kg", value: "74.0", updatedAt: 0 }],
  });
  return dataset;
}
