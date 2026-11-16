# Wishlist

What DinoGym v2 does for its owner, agreed in the wishlist thread starting 2026-11-16. Training content (rules, research, starter programs) lives in [TRAINING.md](TRAINING.md). Decisions are recorded in [DECISIONS.md](DECISIONS.md).

**Status:** in progress. Sections marked _open_ are still being discussed.

## How the owner trains today

- **Style:** calisthenics (bodyweight).
- **Logs:** repetitions only. No holds in seconds, no added weight, no assistance, no RPE, no tempo, no per-side counts. Unilateral work such as "12 each leg" is logged as 12.
- **Fixed-part exercises:** front lever work is logged as one number inside a fixed pattern: `3s hold + {n} pulls + 3s hold`. Only `{n}` changes. v1 already supported this as an exercise template.
- **Show-only items:** some items appear in a routine but are never logged (jump rope at the end of Legs).
- **Equipment:** curls and lateral raises use dumbbells, always at the same weight, so the weight is not logged.
- **Progression:** the owner adds a rep when ready (for example bicep curl 10 → 11). The app does not suggest progressions.

### The owner's first program (calisthenics)

Cycle: **Pull → Push → Legs → Rest → Upper → Lower**, then repeat.

| Routine | Exercise | Target |
|---|---|---|
| Pull | Front lever hold + pulls (`3s + {n} pulls + 3s`) | 4 × 2 sets |
| Pull | Forearm curl | 13 × 3 |
| Pull | Pull-ups | 6 × 2 |
| Pull | Reverse curl | 25 × 2 |
| Pull | Rows ("remo") | 12 × 3 |
| Pull | Bicep curl | 12 × 2 |
| Push | Dips | 12 × 3 |
| Push | Single-arm lateral raises | 10 × 2 |
| Push | Hollow push-ups | 8 × 2 |
| Push | Lateral raises | 14 × 2 |
| Push | Push-ups (triceps) | 8 × 2 |
| Legs | Bulgarian split squat | 12 each leg × 3 |
| Legs | Squats | 15 × 2 |
| Legs | Jump rope | shown, not logged |
| Upper | Front lever hold + pulls (`3s + {n} pulls + 3s`) | 4 × 2 sets |
| Upper | Single-arm lateral raises | 10 × 2 |
| Upper | Dips | 11 × 2 |
| Upper | Neutral-grip pull-ups | 7 × 3 |
| Upper | Lateral raises | 10 × 2 |
| Upper | Reverse curl | 12 × 2 |
| Upper | Push-ups | 12 × 2 |
| Lower | Same as Legs | |

## Programs

- A **program** is an ordered cycle of routines and rest days. A **routine** (v1 called it a workout) is an ordered list of exercises with set targets.
- Creating a program asks for a type: **Gym** or **Calisthenics**.
- The app ships two starter programs, both built from the routines Upper, Lower, Push, Pull and Legs:
  - **Gym UL/PPL**, following Jeff Nippard's public recommendations.
  - **Calisthenics UL/PPL**, following the same principles (close to failure, rest, volume) with higher rep ranges because the load is fixed.
- Users can build their own program from the shipped routines or their own.
- A shorter **Upper / Lower / Rest / Upper / Lower** layout is offered for people with less time. Details are in [TRAINING.md](TRAINING.md).

### How the cycle advances

- The cycle advances by **completed workout**, not by weekday (as in v1). The home screen shows the next routine in the cycle.
- **Rest** is a step in the cycle that the user marks done.
- The user can **skip** a routine (for example Legs) without logging it; the cycle moves on.

### Gym vs Calisthenics

| | Gym | Calisthenics |
|---|---|---|
| Weight field | Shown | Hidden; can be turned on per exercise (for example weighted dips) |
| Default rep ranges | Lower | Higher |
| Starter program | Gym UL/PPL | Calisthenics UL/PPL |

## Progress

- Each exercise has a **progress view** (reference: the owner's screenshot of another app's exercise history: chart of the top set over time, best value, list of records).
- Progress tracks the **first set** of the exercise. Later sets are kept in history but do not count for progress.
- The progress record keeps only the **days the first set improved** on the previous best. In calisthenics an improvement is more reps; with weight, more weight, or the same weight with more reps.
- Full session history (every set) is still saved and editable.

## Exercise images

- Every exercise in the starter library has an image.
- Candidate source: [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (Unlicense, public domain, about 800 exercises with photos). It covers gym exercises well. Calisthenics skills (front lever, hollow push-up, sissy squat, Nordic curl) may be missing and need another source or our own illustrations.
- Images ship inside the app so they work offline.

## Open

- _open_ What annoyed the owner in v1.
- _open_ Must-have for the first daily-usable version, and what comes later.
- _open_ Images for custom exercises and for skills missing from free-exercise-db.
- _open_ Feature needs: data, backend, AWS services, permissions, cost.
