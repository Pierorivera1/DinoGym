// Names for the generated contract types (api/openapi.yaml → schema.d.ts, `npm run gen:api`).
import type { components } from "./schema";

type Schemas = components["schemas"];

export type Session = Schemas["Session"];
export type ChangeSet = Schemas["ChangeSet"];
export type PullResponse = Schemas["PullResponse"];
export type Program = Schemas["Program"];
export type CycleStep = Schemas["CycleStep"];
export type Routine = Schemas["Routine"];
export type Exercise = Schemas["Exercise"];
export type RoutineExercise = Schemas["RoutineExercise"];
export type WorkoutSession = Schemas["WorkoutSession"];
export type SetEntry = Schemas["SetEntry"];
export type WeighIn = Schemas["WeighIn"];
export type Preference = Schemas["Preference"];
