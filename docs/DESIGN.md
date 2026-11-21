# Design guide

Status: approved 2026-11-16 (decision 33). This is the style guide for the Android app (Compose) and, later, the React pages. The reasoning behind the style is in [design/PIXEL-STYLE.md](design/PIXEL-STYLE.md); this file holds the values to build with.

- **Live canvas:** https://claude.ai/artifact/3XzHLFUzvpe5iuf5SrAhXj (private to the owner)
- **Canvas source:** [design/mocks/](design/mocks/). Each `.dc.html` file is one artboard. The values below were copied from them.

If this file and a mock disagree, this file wins. Change this file first, then the mock.

## Screens and their mock status

Decision 16 still applies: a screen is built only after its mock is approved.

| Screen | Mock | Status |
|---|---|---|
| Style tile (tokens, type, components) | `Main.dc.html` | Approved |
| Home | `Home.dc.html` | Approved. Settings gear added to the header 2026-11-16, awaiting the owner's review |
| Active exercise (set logging) | `Workout.dc.html` | Approved |
| Rest time picker (sheet) | `RestPicker.dc.html` | Approved |
| Rest timer | `Rest.dc.html` | Approved |
| Workout summary | `Summary.dc.html` | Approved |
| Programs list (programs and routines) | `ProgramList.dc.html` | Mocked 2026-11-16, awaiting the owner's review. Built (task 7) |
| Program editor (name, type, cycle) | `ProgramEdit.dc.html` | Mocked 2026-11-16, awaiting the owner's review. Built (task 7) |
| Routine editor | `RoutineEdit.dc.html` | Mocked 2026-11-16, awaiting the owner's review. Built (task 7) |
| Exercise picker (sheet) | `ExercisePicker.dc.html` | Mocked 2026-11-16, awaiting the owner's review. Built (task 7) |
| New program (sheet: empty program and starter programs) | `NewProgram.dc.html` | Mocked 2026-11-18, awaiting the owner's review. Built (task 22, decision 92) |
| Exercise library (active / archived) | `ExerciseLibrary.dc.html` | Mocked 2026-11-16, awaiting the owner's review |
| Exercise editor (archive and restore) | `ExerciseEdit.dc.html` | Mocked 2026-11-16, awaiting the owner's review |
| Settings | `Settings.dc.html` | Mocked 2026-11-16, awaiting the owner's review. Built (task 10) |
| Stats: history by date | `StatsHistory.dc.html` | Mocked 2026-11-16, awaiting the owner's review |
| Stats: past session (edit, delete) | `SessionDetail.dc.html` | Mocked 2026-11-16, awaiting the owner's review |
| Stats: progress for one exercise | `Progress.dc.html` | Mocked 2026-11-16, awaiting the owner's review |
| Stats: progress tab (list of exercises with their best) | – | Built without a mock (task 9, decision 47), awaiting the owner's review |
| Body weight chart and weigh-in entry (Stats › WEIGHT) | `BodyWeight.dc.html` | Mocked 2026-11-16, awaiting the owner's review. Built (task 11) |
| Account: sign-in and sync status (Settings › ACCOUNT, sign-out dialog) | `Account.dc.html` | Approved 2026-11-17. Built in task 20; the sync row in task 21 |
| Shared link page (web, `/r/<id>`): program and routine, loading, unknown link, error, not on Android | `SharePage.dc.html` | Mocked 2026-11-18, awaiting the owner's review. Built (task 23) |
| Share a routine or program: SHARE button in the editors and the share sheet's states | `ShareFlow.dc.html` | Mocked 2026-11-18, awaiting the owner's review. Built (task 23) |
| Import a shared link: paste, preview of a program or routine, loading and error states | `ImportShare.dc.html` | Mocked 2026-11-18, awaiting the owner's review. Built (task 23) |
| Reminders: Settings › REMINDERS (signed in, signed out, notifications blocked), the time sheet, the notifications | `Reminders.dc.html` | Approved 2026-11-19, built (task 24, decisions 104–107, 109) |
| Web dashboard: history (stat panels, 18-week day grid, workouts by month, one workout open) | `WebHistory.dc.html` | Approved 2026-11-20 (task 25, decision 110). Built (task 25, decision 112) |
| Web dashboard: progress for one exercise (list, chart with every workout, records) | `WebProgress.dc.html` | Approved 2026-11-20. Built (task 25, decision 112) |
| Web dashboard: body weight (panels, chart, weigh-ins) | `WebWeight.dc.html` | Approved 2026-11-20. Built (task 25, decision 112) |
| Web dashboard at phone width (history, one workout, progress) | `WebPhone.dc.html` | Approved 2026-11-20. Built (task 25, decision 112) |
| Web dashboard: sign-in, loading, nothing synced, errors, session ended | `WebSignIn.dc.html` | Approved 2026-11-20. Built (task 25, decision 112) |

The build order in [ARCHITECTURE.md](ARCHITECTURE.md) is arranged so the code that has no screen, and the approved screens, are built while the remaining mocks are made.

## Tokens

The Compose theme is a custom `DinoColors` object provided through a `CompositionLocal`. M3's `ColorScheme` is filled from these values so stock components behave, but screens read `DinoTheme.colors`. There is no dynamic colour.

| Token | Night | Day | Used for |
|---|---|---|---|
| `bg` | `#0C0915` | `#FBF3E8` | Screen background, text box fill |
| `surface` | `#1C1430` | `#FFFDF8` | Cards, sheets, the bottom bar |
| `surface2` | `#2A1F45` | `#F3E6D6` | Icon tiles, stepper buttons, dotted dividers, sheet title bar (Night) |
| `outline` | `#F4F2F7` | `#33213F` | Borders and main text |
| `muted` | `#A39BB6` | `#7A6684` | Labels, secondary text |
| `faint` | `#6E6584` | `#7A6684` | Picker items two steps from the selection (decoration only) |
| `primary` | `#A98BFF` | `#6B4FC8` | Main action fill, progress blocks, chart line |
| `onPrimary` | `#0C0915` | `#FBF3E8` | Text and icons on `primary` |
| `select` | `#F5D23A` | `#FBDA61` | Selected item, current step, "new best" tag |
| `onSelect` | `#0C0915` | `#33213F` | Text on `select` |
| `action` | `#F0A030` | `#33213F` | Border of secondary buttons and nav tabs |
| `danger` | `#FF4D4D` | `#B8326A` | End workout, delete, errors |
| `success` | `#5CD27A` | `#2F7D45` | Completed set, improvement |
| `shadow` | `#4A3A80` | `#33213F` | Hard 4dp shadow on floating elements |
| `bandHigh` | white 22% | white 60% | Top band of raised components |
| `bandLow` | black 45% | `#33213F` 25% | Bottom band of raised components |
| `scrim` | `#0C0915` 78% | `#33213F` 55% | Behind sheets and dialogs |
| `bestRow` | `#5CD27A` 14% | `#E3F3E6` | Row background for an improved exercise in the summary |

Day-only decorative fills: `pink #F2B6C6`, `teal #9FDCD4` (sheet title bar in Day). Text on them is always `outline`.

### Selected state

The selected state differs by theme, so it has its own three tokens:

| Token | Night | Day |
|---|---|---|
| `selBg` | `surface` | `select` |
| `selBorder` | `select` | `outline` |
| `selText` | `select` | `outline` |

Night marks a selection with a yellow outline and yellow text. Day fills it with butter yellow, because a yellow outline on cream is too faint (1.25:1).

## Type

Fonts are bundled as resources (`res/font/`), never downloaded at runtime. Both are under the OFL.

| Style | Font | Size | Used for |
|---|---|---|---|
| `numberXl` | Pixelify Sans SemiBold | 56–64sp (104sp on the rest timer) | Reps in the stepper, timers |
| `title` | Pixelify Sans SemiBold | 24–40sp | Screen and routine titles (`PULL`) |
| `button` | Pixelify Sans SemiBold | 18–20sp | Button labels |
| `navLabel` | Pixelify Sans SemiBold | 11sp, shrinks to 8sp | Bottom bar labels (see Bottom bar) |
| `heading` | Inter SemiBold | 20–22sp | Exercise name on the active screen |
| `body` | Inter Regular | 15–16sp | Lists, the text box, everything people read |
| `label` | Inter SemiBold, caps, +0.06em | 12–14sp | `REPS`, `UP NEXT`, `CYCLE` |

Pixelify Sans covers á é í ó ú ü ñ ¿ ¡ in both cases (checked on the style tile).

## Shapes and surfaces

- **1 art pixel = 2dp.** Borders are 2dp; the text box border is 3dp.
- **Stepped corners:** a custom `Shape` that cuts a 2dp square out of each corner (the mocks use the polygon in `N` in any `.dc.html`). Never a radius.
- **Raised:** a 2dp `bandHigh` line inside the top edge and a 4dp `bandLow` band inside the bottom edge. Pressed: move down 2dp and drop the bottom band. No ripple.
- **Hard shadow:** 4dp right and 4dp down, `shadow` colour, no blur. Only on floating things: the active set card, the text box, dialogs.
- **Spacing:** 4dp scale. Screen side padding 16dp. Gaps between sections 14–16dp.
- **Touch targets:** at least 48×48dp. Steppers are 64dp, main buttons 56–64dp tall.

## Components

| Component | Spec |
|---|---|
| **Primary button** | `primary` fill, `outline` 2dp border, stepped, raised, optional 16×16 icon at 2× on the left, `button` text |
| **Secondary button** | `surface` fill, `action` 2dp border, stepped, raised |
| **Danger icon button** | Transparent, `danger` border and icon (the ✕ that ends a workout) |
| **Text box** | `bg` fill, 3dp `outline` border, hard shadow, `body` text starting with `* `. Optional 48dp mascot on the left. Typewriter at about 40 chars/s; tap skips; off with reduced motion |
| **Stat bar** | `label`, a row of 2dp-outlined blocks (filled `primary`, current `select`, empty transparent), then `current/total` in Pixelify |
| **Stepper** | `label` on the left; − and + 64dp `surface` buttons around the number in `numberXl` |
| **Set row (done)** | `surface`, `success` border, check icon, set label, reps; a `select` tag `▲ NEW BEST` / `▲ RÉCORD` when set 1 beats the previous best |
| **Active set card** | `selBg` / `selBorder` / `selText`, hard shadow, cursor before `SET 2 OF 2`, stepper, "Pre-filled from last session" |
| **Window sheet** | Title bar (`surface2` in Night, `teal` in Day) with a square close box; body on `surface`; `scrim` behind |
| **Scroll picker** | Two wheels (MIN, SEC). The selected row sits in a stepped `select`-bordered band with the cursor at its left. Neighbours shrink (40 → 26 → 18sp) and fade (`outline` → `muted` → `faint`). Seconds move in 5-second steps; range 0:00–9:00 |
| **Cycle strip** | One stepped box per step in the program cycle. Current: `sel*` colours and the cursor. Rest days show `ZZZ` in `muted` |
| **Cursor** | Dinocito's 8×8 face (`CUR_O`, `CUR_Y` in the mocks), drawn at 2×–4×. Marks the current item; replaces the icon on a selected tab |
| **Icons** | Our own 16×16 bitmaps drawn at 2× or 3×, one colour, `crispEdges`. The first set (home, programs, start, stats, exercises, check, timer, plus, minus, close, edit, skip, pull) is in `Main.dc.html`. The second set (gear, back, chevron, drag, trash, search, archive, zzz, and the exercise types lever, row, curl, raise, dip, push-up, squat, rope) is in the `px([...])` tables of the boards that use it (`ExerciseEdit.dc.html` has all the exercise types). All are first drafts to redraw as vector drawables. A chevron pointing down or up is the same bitmap rotated 90° |
| **Section header** (new) | `label` on the left, a 48dp secondary `+ NEW` button on the right. The add action for every list (programs, routines, exercises) |
| **Top bar** (new) | 56dp grid `48 / 1fr / 48`: back (secondary icon button, `action` border, back arrow), title in `title` 22sp, then a danger trash button or an empty cell |
| **Text field** (new) | `label` above; 52dp box, `bg` fill, 2dp `outline` border, stepped, `body` 16sp. No focus colour change in the mocks |
| **Segmented control** (new) | 2–4 equal boxes, 4dp gaps, 48dp tall. Unselected: secondary button. Selected: `select` fill, `onSelect` text, `outline` border, no bands (looks pressed). Used as tabs (Stats, library filter) and as a radio group (type, kind, reps mode, theme) |
| **Switch** (new) | 60×32 stepped box, 2dp `outline` border, 4dp padding, 20dp square knob. Off: `surface2` fill, knob `outline`, left. On: `primary` fill, knob `onPrimary`, right. The touch target is 64×48 |
| **Reorderable row** (new) | 40dp drag handle (drag icon in `muted`) on the left, content, a 48dp remove button on the right. Long-press the handle to drag; TalkBack gets "Move up" / "Move down" actions |
| **Checkbox** (new) | 28dp square, 2dp `outline` border. Checked: `select` fill with the check icon in `onSelect`. The whole row is the target |
| **Set chip** (new) | 48×52 stepped box: `SET n` (`S n` in Spanish) over the reps in Pixelify; a `success` ▲ marks a set-1 record. Selected chip uses `sel*` colours |
| **Chart** (new) | Stepped line, 2dp `primary` stroke, flat `primary` tint under it (20% Night, 15% Day), dotted 2dp grid lines in `muted` at 40%, 4dp square points, latest point 8dp `select` with an `outline` border. Axis labels Inter 11sp `muted` |

## Bottom bar

- Five stepped tabs with 4dp gaps and 6dp side padding: HOME, PROGRAMS, START, STATS, EXERCISES / INICIO, PROGRAMAS, EMPEZAR, STATS, EJERCICIOS.
- Icon on top (32dp), label below.
- Normal tab: `surface`, `action` border. Selected: `sel*` colours, cursor instead of the icon.
- START: `primary` fill, 76dp tall against 64dp, raised 20dp above the bar. It starts the next routine in the cycle.
- **Label width (measured 2026-11-16 from the font's metrics):** at 11sp the longest label, `EJERCICIOS`, is 60.5dp; a tab on a 360dp phone has about 62dp inside its border. It fits at the default font size. Labels use Compose's text auto-size between 8sp and 11sp so they still fit with larger system fonts; they never wrap or ellipsize. Each tab also has a content description, so screen readers get the full word.
- The bar is hidden during a workout (active exercise, rest timer, summary). Those screens are a focused flow with ✕ to end it.

## Screen notes

These record behaviour shown in the approved mocks.

- **Home:** text box with the next routine and the last set-1 result for it; the Up next card lists every exercise as `sets × reps`; an edit button on the card opens the routine editor (fixes the version 1 complaint about finding edit); START and SKIP; cycle strip; last workout row.
- **Active exercise:** top bar with ✕ (end), routine name and elapsed time; `EXERCISE n/N` stat bar; exercise tile, name and `sets × reps · last time`; done sets as set rows; the active set card pre-filled with last session's number; LOG SET; a row of rest time (opens the picker), NOTE and SKIP; NEXT exercise at the bottom. The weight field appears only for Gym programs or when turned on for the exercise (decision 26).
- **Rest time picker:** opened from the rest button. Applies to that exercise in that routine. Shortcuts: 1:30 isolation, 2:30 compound (TRAINING.md defaults).
- **Rest timer:** `numberXl` timer, 10-block bar, −15s / +15s, mascot text box with what comes next, SKIP REST, and a note that it keeps running with the screen off (a notification countdown and an alarm, decision 42).
- **Summary:** title, routine and duration, text box naming any new best, TIME / SETS / BESTS panels, set-1 reps per exercise against last time (`=` or `▲ +n`), next step in the cycle, EDIT and FINISH.

These record behaviour shown in the mocks of 2026-11-16 that await review:

- **Navigation:** the bar shows on the four tab screens (Home, Programs list, Stats, Exercise library). Editors, the past session, progress and Settings are pushed screens with the top bar (back on the left) and no bottom bar, like the workout flow.
- **Saving:** editors have no SAVE button. Every change is written to Room as it happens (as sets already are), and each editor says "Changes save as you go". Removing an item from a list (a cycle step, an exercise from a routine) happens at once with an UNDO snackbar. Deleting a whole program, routine or session (the danger trash in the top bar) asks first in a dialog, because it leaves the screen. All deletes are soft (`deletedAt`).
- **Creating:** `+ NEW` creates the row with a default name ("New program", "New routine", "New exercise") and opens its editor with the name field focused.
- **Programs list:** two sections, YOUR PROGRAMS and ROUTINES, each with `+ NEW` in its header. The active program uses the `sel*` colours, the cursor and an `ACTIVE` tag, and shows its cycle strip with the next step marked. Tapping a program or routine opens its editor. Routines are listed on their own because one routine can be used in several programs.
- **Program editor:** NAME, TYPE (Gym / Calisthenics segmented control), CYCLE as reorderable rows (step number, routine name and exercise count, or a rest day with the zzz icon; the next step has a `NEXT` tag). Tapping a routine step opens the routine editor; ✕ removes the step only, never the routine. `+ ROUTINE` opens a sheet listing the routines plus "New routine"; `+ REST DAY` appends a rest step. An inactive program shows a primary `USE THIS PROGRAM` button at the bottom (not drawn).
- **Routine editor:** NAME, then the exercises as reorderable cards showing `sets × reps · rest`. Tapping a card expands it in place (one at a time, `sel*` colours and the hard shadow): SETS stepper, REPS mode (FIXED / RANGE; RANGE shows two steppers, from and to), REST button opening the rest picker, and a trash button that removes the exercise from this routine. `+ ADD EXERCISE` is a primary button fixed at the bottom. Also reached from the edit button on Home's Up next card.
- **Exercise picker:** a full-height window sheet over the routine editor. Search field at the top; results show icon, name, kind, and an `IN ROUTINE` tag. Rows are checkboxes, so several exercises can be added at once with `ADD n EXERCISES`. `+ NEW EXERCISE "<query>"` opens the exercise editor with the query as the name; coming back, the new exercise is ticked. Archived exercises do not appear.
- **Exercise library:** `+ NEW` in the header; ACTIVE / ARCHIVED segmented control with counts; a three-column grid of tiles (56dp `surface2` icon tile at 3×, name under it, up to three lines), alphabetical. Tapping a tile opens the exercise editor.
- **Exercise editor:** NAME, ICON (radio grid of the 10 type icons), HOW IT IS LOGGED (REPS / PATTERN / SHOW ONLY). PATTERN shows the template field with `{n}` and a live preview. Log weight switch (decision 26). ARCHIVE at the bottom, with a note of which routines use it; archiving keeps history and progress. An archived exercise opens with RESTORE in place of ARCHIVE. Exercises are never hard-deleted from the UI.
- **Settings:** reached from a 48dp gear button in Home's header (secondary style, next to the date). THEME segmented control (System / Night / Day, default System). LANGUAGE is a read-only box saying the app follows the phone (English or Spanish). ABOUT: version, source code link (opens GitHub in the browser), open-source licences screen, MIT licence. A mascot text box at the bottom.
- **Stats history:** HISTORY / PROGRESS segmented control at the top. History groups finished sessions by month, newest first; each row has a date tile, routine, duration and set count, and a `▲ n BEST` tag when it set records. Rest days and skips are not sessions, so they do not appear. PROGRESS lists every active exercise with its best set 1 and opens the progress view (not drawn).
- **Past session:** title is the routine; date, duration and sets under it; trash deletes the session after a dialog (the cycle does not move back). One card per exercise with set chips. Tapping a chip opens a stepper under that card; the value saves as it changes and set-1 changes recompute progress. Tapping the exercise name opens its progress.
- **Progress:** exercise tile and name; BEST / GAIN / RECORDS panels; the stepped chart of set-1 records over all time (one step per record day, months on the x axis); RECORDS list newest first with `▲ +n`, the first entry marked `FIRST`. With weight, a record is more weight or the same weight with more reps, shown as `20 kg × 8`.
- **Account (task 20):** sign-in lives only in Settings, as an ACCOUNT section above THEME; there is no first-launch prompt, because everything works without an account. Signed out: a card explaining backup and sync, `SIGN IN WITH GOOGLE` (primary), which opens Android's Credential Manager sheet, and a note that DinoGym only gets the name and email. Signed in: a tile with the initial (no profile photo, so nothing loads from the network), name and email, a sync status row (`success` check and "Synced · 2 min ago", or the hourglass and "Offline · n changes waiting"), `SYNC NOW` (disabled offline) and `SIGN OUT`. Sign out asks first in a dialog; the workouts stay on the phone and unsynced changes are kept for the next sign-in with the same account.
  - **Built in task 20 without the sync row (decision 84):** the sync status row and `SYNC NOW` come with sync in task 21; until then SIGN OUT is full width and the note says sync arrives in the next update. The sign-out dialog is the app's `ConfirmSheet` with a primary SIGN OUT. Sign-in errors are a `danger` line inside the card; while signing in the button is disabled and reads SIGNING IN…. The cloud icon is `ic_px_cloud`.
  - **Sync row built in task 21 (decisions 88–91):** under a dotted line: the `success` check and "Synced · 2 min ago" (just now / min / h / d); the hourglass `ic_px_wait` (mock `I.wait`) with "Offline", "Waiting to sync" or "Syncing…" and "· n changes waiting"; "Sync failed" in `danger` with its note. `SYNC NOW` and `SIGN OUT` share the row as two secondary buttons; SYNC NOW is disabled (the disabled primary look) while offline or syncing. The note under the buttons follows the state (synced, offline, failed). The sign-out dialog adds "n changes have not synced yet: they sync the next time you sign in with this account" when there are some.
- **Shared link page (task 23, decisions 98 and 100):** the web page behind every shared link, drawn as a phone browser because that is where links are opened. DINOGYM wordmark with the cursor; `SHARED PROGRAM` / `SHARED ROUTINE` label, name in `title` 32sp, type and number of days (or exercises); the mascot text box; `OPEN IN DINOGYM` (primary) and `GET THE APP` (secondary) with a note that the APK is on GitHub Releases; for a program the cycle strip (rest days `ZZZ` in `muted`) and one card per routine listing `sets × reps · rest`, a pattern exercise with its pattern, a `KG` tag when weight is logged. Night or Day follows the browser's colour scheme, English or Spanish its language. Pixelify Sans is served with the page; body text uses the system sans-serif instead of Inter (860 KB). States: loading, unknown link (`danger`), connection error with TRY AGAIN, and a note instead of OPEN on phones that are not Android.

- **Share (task 23, decision 103):** a secondary `SHARE ROUTINE` / `SHARE PROGRAM` button with the new share icon (`ic_px_share`) ends the routine editor's list and sits under the program editor's cycle. Its window sheet, SHARE: "Making the link…"; the link in a `bg` box with CLOSE and SEND LINK (Android's share sheet); signed out, with CANCEL and SETTINGS; offline and errors with TRY AGAIN; the limits (20 a day, too big, no routine in the cycle) in `danger` with CLOSE.
- **Import (task 23, decision 102):** a pushed screen, IMPORT, with the top bar. Paste: a SHARED LINK field and OPEN LINK, with an error under the field when the text holds no link. Preview: SHARED PROGRAM / SHARED ROUTINE, the name in `title` 28sp, type and days or the exercise count, the mascot text box saying what ADD does (and whether the program becomes active), the compact cycle strip, one card per routine with 40dp exercise tiles and `sets × reps · rest`, a filled NEW tag on exercises the library lacks, then ADD PROGRAM / ADD ROUTINE fixed at the bottom (ADDING… while it writes). The `+ NEW` program sheet of task 22 gains a FROM A LINK row under EMPTY PROGRAM.

- **Reminders (task 24, decision 104):** a REMINDERS section between ACCOUNT and THEME. Signed in: a card with two rows, each a title, a one-line explanation and a Switch: Workout day (with a secondary TIME button under it showing the time in the phone's 12 or 24-hour format) and Weekly summary (Sundays at 20:00). A note under the card says reminders follow the account and the phone's time zone. TIME opens a window sheet with the scroll picker (HOUR, MIN in 5-minute steps) and SAVE. Signed out: a muted card with the bell icon saying to sign in under ACCOUNT. When Android blocks DinoGym's notifications: a `danger` line and ALLOW NOTIFICATIONS at the top of the card (asks, or opens the app's notification settings once asking is no longer allowed). The notifications are drawn by Android, with the bell as the small icon in `primary`.

- **Web dashboard (task 25, decision 110):** read-only, at the root of the same CloudFront host as the shared link page. Signed out: the DINOGYM wordmark, a title, the mascot text box saying logging stays on the phone, Google's own Sign in with Google button (drawn by Google Identity Services, not in our style), a privacy line and GET THE APP. Signed in, a header with the wordmark, three tabs as a segmented control (HISTORY, PROGRESS, WEIGHT, each its own URL), "Updated n min ago", REFRESH, the account initial and name, and SIGN OUT. **History** (the landing page): four stat panels (this week, this month, time this month, bests this month) and a grid of the last 18 weeks, one square a day; workouts by month on the left, the selected one open on the right with its exercises and set chips (read-only), names linking to progress. **Progress:** a searchable list of exercises with their best set 1; the selected exercise's chart shows the stepped best line plus every workout's set 1 as small `muted` squares, with a 3M / 6M / 1Y / ALL range; BEST / GAIN / RECORDS panels; the records list with the routine. **Weight:** CURRENT / CHANGE / GOAL panels, the chart (7-day average, weigh-ins, dashed goal, 1M / 3M / 1Y / ALL), and the weigh-ins list. Nothing can be logged or edited; each view says so. **Phone width:** one column; the account becomes a 48dp button (REFRESH and SIGN OUT behind it), the tabs fill the width, a workout opens as its own page with a back button, and the progress exercise list becomes a select. **States:** loading, nothing synced yet (points to Settings › ACCOUNT in the app), sign-in failed and could not load (`danger`, TRY AGAIN), and session ended (a line on the sign-in page). Night or Day and English or Spanish follow the browser, as on the shared link page.

## Theme setting

The app follows the system dark mode by default: dark is Night, light is Day. Settings can force one.

## Strings

Every string in the mocks exists in English and Spanish. The Spanish copy in the mocks is the starting translation. Routine names (`PULL`, `PUSH`…) are user data and are not translated.
