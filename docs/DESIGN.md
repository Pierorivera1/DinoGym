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
| Home | `Home.dc.html` | Approved |
| Active exercise (set logging) | `Workout.dc.html` | Approved |
| Rest time picker (sheet) | `RestPicker.dc.html` | Approved |
| Rest timer | `Rest.dc.html` | Approved |
| Workout summary | `Summary.dc.html` | Approved |
| Programs: list, program editor (cycle), routine editor, exercise picker | – | **Needs a mock**, part of the first version |
| Exercises: library, edit, archive and restore | – | **Needs a mock**, part of the first version |
| Stats: history by date, edit a past session, progress view per exercise | – | **Needs a mock**, part of the first version |
| Settings (theme: system / Night / Day) | – | **Needs a mock**, part of the first version |
| Body weight chart and weigh-in entry | – | Needs a mock (after the first version) |
| Sign-in, sync status, sharing, reminders, web | – | Later |

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
| **Icons** | Our own 16×16 bitmaps drawn at 2× or 3×, one colour, `crispEdges`. The first set (home, programs, start, stats, exercises, check, timer, plus, minus, close, edit, skip, pull) is in `Main.dc.html` and is a first draft to redraw as vector drawables |

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
- **Rest timer:** `numberXl` timer, 10-block bar, −15s / +15s, mascot text box with what comes next, SKIP REST, and a note that it keeps running with the screen off (foreground service).
- **Summary:** title, routine and duration, text box naming any new best, TIME / SETS / BESTS panels, set-1 reps per exercise against last time (`=` or `▲ +n`), next step in the cycle, EDIT and FINISH.

## Theme setting

The app follows the system dark mode by default: dark is Night, light is Day. Settings can force one.

## Strings

Every string in the mocks exists in English and Spanish. The Spanish copy in the mocks is the starting translation. Routine names (`PULL`, `PUSH`…) are user data and are not translated.
