# Wishlist

What DinoGym v2 does for its owner, agreed in the wishlist thread starting 2026-11-16. Training content (rules, research, starter programs) lives in [TRAINING.md](TRAINING.md). Decisions are recorded in [DECISIONS.md](DECISIONS.md).

**Status:** agreed 2026-11-16.

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
| Progression | Fixed sets. Add reps inside 6–10; at 10, add weight and drop back to 6. | Fixed sets. Add reps when ready. |

Set volume is not tracked or targeted. The number of sets in a routine stays as written.

## Progress

- Each exercise has a **progress view** (reference: the owner's screenshot of another app's exercise history: chart of the top set over time, best value, list of records).
- Progress tracks the **first set** of the exercise. Later sets are kept in history but do not count for progress.
- The progress record keeps only the **days the first set improved** on the previous best. In calisthenics an improvement is more reps; with weight, more weight, or the same weight with more reps.
- Full session history (every set) is still saved and editable.

## Body weight

- A **body weight chart** (reference: the owner's screenshot of another app, 2026-11-16: a line of weigh-ins over time with a goal line). Drawn in our pixel style, see "Charts" in [design/PIXEL-STYLE.md](design/PIXEL-STYLE.md); the mock goes through the Design thread like every screen.
- **Logging:** a `+ LOG` button adds a weigh-in in kilograms for a date, default today. One weigh-in per day; logging again the same day replaces it. Entries can be edited and deleted.
- **Chart:**
  - Ranges **1M / 3M / 1Y / ALL**, as a segmented control.
  - A stepped line on the pixel grid with a light fill under it. The latest weigh-in is a bigger highlighted point.
  - Y axis in kg, scaled to the data in range; X axis labelled by month.
  - Tapping a point shows its date and weight.
- **Goal:** an optional target weight, shown in the header next to a target icon and drawn as a dashed line across the chart in a contrasting colour.
- Works offline and syncs like workouts. The web dashboard shows the same chart.
- Applies to both Gym and Calisthenics programs.
- **7-day average:** the chart draws a 7-day trailing average as the main line, with the raw weigh-ins as small points. Daily weight swings by about 0.5–1 kg from water and food, so the average shows the real trend.
- **Goal direction** is inferred, not chosen: a goal below the first weigh-in in range means losing, above means gaining. Moving toward the goal is coloured as progress. No separate lose/gain/maintain setting.

## Exercise images

- **No photos.** Each exercise shows a small pixel icon for its type, as in the library tile in [design/PIXEL-STYLE.md](design/PIXEL-STYLE.md). The icons are drawn by us, so there is no licence question.
- The starter library stays small: only the exercises the starter programs use.

## What went wrong in v1

- **Hard to find how to add or edit a workout.** Creating and editing routines and programs must be obvious from the main screens.
- **The rest timer could not be set by scrolling.** v2 uses a scroll picker for minutes and seconds.

## First daily-usable version (MVP)

The owner switches from v1 when these exist:

1. Run the owner's program and log reps (the program typed in as starter data).
2. Add and edit routines and the program (fixes the v1 complaint above).
3. Rest timer with a scroll picker, running in the background.
4. History of sessions, editable.
5. Progress view per exercise (first-set rule above).
6. Exercise icons.

English and Spanish strings apply from the start (product rule), even though it is not a feature to switch for.

**Later**, roughly in this order: body weight (local only, small), sign-in and sync, the starter UL/PPL programs and a program builder for other users, shareable routines, reminders, web dashboard.

## What each feature needs

Estimated 2026-11-16. "Free tier" means the always-free allowances (Lambda 1M requests a month, DynamoDB 25 GB, CloudFront 1 TB out, EventBridge Scheduler 14M invocations). At a handful of users, every feature is expected to cost close to $0 a month, well under the $10 cap (decision 6). Verify the free-tier terms when the AWS account is created, since AWS changed them for new accounts in 2025.

| Feature | Data (on the phone) | Backend and AWS | Android permissions | Cost | Outside STACK.md |
|---|---|---|---|---|---|
| Programs, routines, logging, history | Room: programs, routines, exercises, sessions, sets | None until sync | None | $0 | No |
| Rest timer | Rest per exercise | None | `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` plus a service type (decide at build time; `shortService` is capped at 3 min, too short for a 9-minute rest) | $0 | No |
| Progress view | Computed from sets (set 1 per session) | None. The web computes it from synced data | None | $0 | No |
| Exercise icons | Bundled pixel drawables | None | None | $0 | No (drawn by us) |
| Starter programs | Bundled data, copied into Room on first run | None | None | $0 | No |
| Body weight | Room: one weigh-in per day, optional goal | Syncs like workouts | None | $0 | No |
| Sign-in | Session token, kept encrypted on the phone | Lambda verifies the Google ID token and issues sessions; signing key in a secret store | `INTERNET` | $0 | Secret store: likely SSM Parameter Store (already in OPEN-ITEMS) |
| Sync | Change tracking per row (sync design still open) | API Gateway HTTP API, Lambda, DynamoDB; WorkManager on the phone | `INTERNET`, `ACCESS_NETWORK_STATE` | Free tier | No |
| Shareable routines | Import from a link | Public read-only page on S3 + CloudFront, plus an API call to publish | None (App Links need `assetlinks.json` served from the CloudFront domain) | Free tier | No. Links change if the CloudFront distribution is recreated (decision 23) |
| Reminders | Reminder settings | EventBridge Scheduler → Lambda → Firebase Cloud Messaging | `POST_NOTIFICATIONS` | Free tier | Keyless auth to FCM is still open (OPEN-ITEMS) |
| Web dashboard | – | React on S3 + CloudFront, same API, Google sign-in on the web | – | Free tier | Google Identity Services for web sign-in (part of Google sign-in; the `*.cloudfront.net` origin check is in OPEN-ITEMS) |

Nothing on this list needs a new AWS service beyond STACK.md. SQS has no user yet. It stays in STACK.md for jobs that need retries, such as sending reminders in bulk.
