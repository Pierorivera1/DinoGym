# Training content

What the app ships and which training rules it follows. Agreed in the wishlist thread on 2026-11-16. The product requirements are in [WISHLIST.md](WISHLIST.md). The cited research is in [research/training-principles.md](research/training-principles.md) and [research/exercise-selection.md](research/exercise-selection.md).

## Scope

- The app covers both **Gym** and **Calisthenics** programs. The scope is no longer bodyweight-only (decision 26).
- The starter programs follow **Jeff Nippard's free public advice**: his YouTube videos, his blog, and the weekly layouts on his program pages.
- Nothing is copied from his paid programs. Their exercise lists and set tables are paid content, and this repository is public.
- The app names Nippard only as the source of the principles. It never calls a routine "Nippard's program".

## Rules the app follows

| Rule | Gym | Calisthenics | Basis |
|---|---|---|---|
| Rep range | 6–10 for every exercise, editable per exercise | A fixed target or a range, set per exercise. Usually higher, for example 8–15 | Owner's choice, inside Nippard's "most volume at 6–15" guidance [F3] |
| Progression | Add reps until set 1 reaches 10, then add weight and restart at 6 | Add reps when ready. Move to a harder variation is the user's call | Owner's choice; matches Nippard's double progression [PMG] |
| Sets | Fixed as written in the routine. The app does not track or target weekly volume | Same | Owner's choice |
| Effort | Guidance text only: stop most sets 1–2 reps short of failure, and take the last set to failure except heavy squats, bench presses and deadlifts. Effort is not logged | Same; bodyweight sets need to get close to failure to work [HOME], [S17] | [BLOG], [PMG] |
| Default rest | 2:30 after compound exercises, 1:30 after isolation exercises. Editable with the scroll picker, up to 9:00 | Same | [F5], [UPPL] |
| Frequency | Each muscle about twice a week, through the UL/PPL layout | Same | [BRO] |
| Suggestions | None. The app pre-fills the last session's numbers and never tells the user to progress | Same | Owner's choice |
| Deloads | Not built in | Same | Owner's choice; [DEL] for later |

The keys in brackets are defined in the research notes.

## Program layouts

Each program is an ordered cycle that advances by completed workout (see [WISHLIST.md](WISHLIST.md)).

| Program | Cycle | Source |
|---|---|---|
| UL/PPL, 5 days | Upper, Lower, Rest, Push, Pull, Legs, Rest | Nippard's Essentials page [ESS]; his 2025 ranking puts UL/PPL in the top tier [SPLIT] |
| Upper/Lower, 4 days (for less time) | Upper, Lower, Rest, Upper, Lower, Rest, Rest | Essentials page, 4-day layout [ESS]; 4-day Upper/Lower is also top tier [SPLIT] |
| Owner's program | Pull, Push, Legs, Rest, Upper, Lower | Owner; close to the Ultimate PPL page layout [UPPL] |

No source was found in which Nippard says Upper/Lower/Rest/Upper/Lower is optimal for people with less time. The citation is his Essentials program page, which is built for busy people.

## Starter routines: Gym

The tiers come from Nippard's free ranking videos, as reported in [research/exercise-selection.md](research/exercise-selection.md). Every exercise uses the 6–10 rep range.

| Routine | Exercise | Sets | Rest |
|---|---|---|---|
| Upper | Incline dumbbell press | 3 | 2:30 |
| Upper | Chest-supported row | 3 | 2:30 |
| Upper | Lat pulldown | 2 | 2:30 |
| Upper | Cable lateral raise | 2 | 1:30 |
| Upper | Overhead cable triceps extension | 2 | 1:30 |
| Lower | Hack squat or back squat | 3 | 2:30 |
| Lower | Romanian deadlift | 3 | 2:30 |
| Lower | Seated leg curl | 2 | 1:30 |
| Lower | Leg extension | 2 | 1:30 |
| Lower | Standing calf raise | 3 | 1:30 |
| Push | Machine chest press or bench press | 3 | 2:30 |
| Push | Seated cable fly | 2 | 1:30 |
| Push | Dumbbell lateral raise | 3 | 1:30 |
| Push | Skullcrusher | 2 | 1:30 |
| Pull | Pull-up | 3 | 2:30 |
| Pull | One-arm dumbbell row | 3 | 2:30 |
| Pull | Reverse pec deck | 2 | 1:30 |
| Pull | Preacher curl | 2 | 1:30 |
| Legs | Bulgarian split squat | 3 | 2:30 |
| Legs | Leg press | 3 | 2:30 |
| Legs | 45° back extension | 2 | 1:30 |
| Legs | Seated calf raise | 3 | 1:30 |

## Starter routines: Calisthenics

The equipment is a pull-up bar, dip bars, the floor and one pair of dumbbells at a fixed weight for curls and raises. This matches how the owner trains.

| Routine | Exercise | Sets × reps | Rest |
|---|---|---|---|
| Upper | Neutral-grip pull-up | 3 × 5–10 | 2:30 |
| Upper | Dip | 3 × 8–12 | 2:30 |
| Upper | Pike push-up | 2 × 6–12 | 2:30 |
| Upper | Dumbbell lateral raise | 2 × 12–20 | 1:30 |
| Upper | Dumbbell curl | 2 × 10–15 | 1:30 |
| Lower | Bulgarian split squat | 3 × 10–15 each leg | 2:30 |
| Lower | Nordic curl negative | 3 × 4–8 | 2:30 |
| Lower | Single-leg hip thrust | 3 × 10–20 | 1:30 |
| Lower | Single-leg calf raise | 3 × 10–20 | 1:30 |
| Push | Dip | 3 × 8–12 | 2:30 |
| Push | Deficit or regular push-up | 3 × 8–15 | 2:30 |
| Push | Dumbbell lateral raise | 3 × 12–20 | 1:30 |
| Push | Diamond push-up | 2 × 8–15 | 1:30 |
| Pull | Pull-up | 3 × 5–10 | 2:30 |
| Pull | Inverted row | 3 × 8–15 | 2:30 |
| Pull | Dumbbell reverse curl | 2 × 12–20 | 1:30 |
| Pull | Dumbbell curl | 2 × 10–15 | 1:30 |
| Legs | Squat (bodyweight, or pistol when 25+ reps is easy) | 3 × 15–25 | 2:30 |
| Legs | Walking lunge | 3 × 12–20 each leg | 1:30 |
| Legs | Single-leg calf raise | 3 × 10–20 | 1:30 |
| Legs | Jump rope | shown, not logged | none |

Lower and Legs use different movements, so the hamstrings and glutes are trained (Nordic negatives, hip thrusts) and not only the quads. The owner's own program keeps Lower identical to Legs. That is his choice; the gap is only noted here.

## Owner's program

This is the first data the app ships with for the owner. The table is in [WISHLIST.md](WISHLIST.md#the-owners-first-program-calisthenics).

## Later

- **Progression ladders** for calisthenics (knee push-up → push-up → archer push-up, and so on), shown as hints. The research notes list them.
- **Deload reminders.**
- **Check before shipping:** the r/bodyweightfitness Recommended Routine wiki against the mirror the research used.
