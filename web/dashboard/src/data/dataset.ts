import type {
  ChangeSet,
  CycleStep,
  Exercise,
  Preference,
  Program,
  Routine,
  RoutineExercise,
  SetEntry,
  WeighIn,
  WorkoutSession,
} from "../api/types";

/** Every live row the account has synced, by id (preferences by key). */
export interface Dataset {
  programs: Map<string, Program>;
  cycleSteps: Map<string, CycleStep>;
  routines: Map<string, Routine>;
  exercises: Map<string, Exercise>;
  routineExercises: Map<string, RoutineExercise>;
  sessions: Map<string, WorkoutSession>;
  setEntries: Map<string, SetEntry>;
  weighIns: Map<string, WeighIn>;
  preferences: Map<string, Preference>;
}

export function emptyDataset(): Dataset {
  return {
    programs: new Map(),
    cycleSteps: new Map(),
    routines: new Map(),
    exercises: new Map(),
    routineExercises: new Map(),
    sessions: new Map(),
    setEntries: new Map(),
    weighIns: new Map(),
    preferences: new Map(),
  };
}

type Row = { updatedAt: number; deletedAt?: number | null };

function apply<T extends Row>(target: Map<string, T>, rows: T[] | undefined, key: (row: T) => string): void {
  for (const row of rows ?? []) {
    // Pages arrive in change-number order, so a later copy of a row is newer.
    if (row.deletedAt != null) target.delete(key(row));
    else target.set(key(row), row);
  }
}

/** Applies one pulled page in place. Deleted rows are dropped (soft deletes, decision 40). */
export function applyChanges(dataset: Dataset, changes: ChangeSet): void {
  const byId = (row: { id: string }) => row.id;
  apply(dataset.programs, changes.programs, byId);
  apply(dataset.cycleSteps, changes.cycleSteps, byId);
  apply(dataset.routines, changes.routines, byId);
  apply(dataset.exercises, changes.exercises, byId);
  apply(dataset.routineExercises, changes.routineExercises, byId);
  apply(dataset.sessions, changes.sessions, byId);
  apply(dataset.setEntries, changes.setEntries, byId);
  apply(dataset.weighIns, changes.weighIns, byId);
  apply(dataset.preferences, changes.preferences, (row) => row.key);
}
