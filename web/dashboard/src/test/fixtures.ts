import type { SetEntry, WorkoutSession } from "../api/types";
import { applyChanges, emptyDataset, type Dataset } from "../data/dataset";

export const H = 3_600_000;
export const D = 24 * H;

let n = 0;
export const id = (prefix: string) => `${prefix}-${String(++n).padStart(36 - prefix.length - 1, "0")}`;

export function workout(ds: Dataset, routineId: string, startedAt: number, sets: [exerciseId: string, reps: number[]][], minutes = 40): WorkoutSession {
  const session: WorkoutSession = {
    id: id("s"),
    routineId,
    programId: "p",
    startedAt,
    endedAt: startedAt + minutes * 60_000,
    currentPosition: 0,
    updatedAt: startedAt,
  };
  const entries: SetEntry[] = sets.flatMap(([exerciseId, reps], i) =>
    reps.map((r, j) => ({ id: id("e"), sessionId: session.id, exerciseId, setIndex: j + 1, reps: r, updatedAt: startedAt + i * 1000 + j })),
  );
  applyChanges(ds, { sessions: [session], setEntries: entries });
  return session;
}

export function dataset(): Dataset {
  const ds = emptyDataset();
  applyChanges(ds, {
    routines: [
      { id: "pull", name: "PULL", updatedAt: 0 },
      { id: "push", name: "PUSH", updatedAt: 0 },
    ],
    exercises: [
      { id: "pu", name: "Pull-ups", iconKey: "pull", kind: "REPS", weightEnabled: false, archived: false, updatedAt: 0 },
      { id: "row", name: "Rows", iconKey: "row", kind: "REPS", weightEnabled: false, archived: false, updatedAt: 0 },
    ],
    routineExercises: [
      { id: "re1", routineId: "pull", exerciseId: "row", position: 0, sets: 2, repMin: 8, repMax: 12, restSeconds: 90, updatedAt: 0 },
      { id: "re2", routineId: "pull", exerciseId: "pu", position: 1, sets: 3, repMin: 5, repMax: 8, restSeconds: 150, updatedAt: 0 },
    ],
  });
  return ds;
}
