import type { Dataset } from "../data/dataset";
import { language, strings as s } from "../i18n";
import { byMonth, dayGrid, panels, workoutExercises, type Workout } from "../rules/history";
import { clock, hoursMinutes, kgShort, longDay, monthHeading, shortDay, weekdayTag } from "../ui/format";
import { backIcon, chevronIcon } from "../ui/icons";
import type { Model } from "../ui/model";
import { Cursor, Icon } from "../ui/Pixel";
import { Link } from "./Link";

interface Props {
  dataset: Dataset;
  model: Model;
  workoutKey: string | null;
  phone: boolean;
  now: number;
  timeZone: string;
}

export function HistoryView({ dataset, model, workoutKey, phone, now, timeZone }: Props) {
  const { workouts } = model;
  const selected = workoutKey
    ? workouts.find((w) => model.workoutKey.get(w.id) === workoutKey)
    : phone
      ? undefined
      : workouts[0];

  // On a phone, one workout replaces the list (WebPhone.dc.html).
  if (phone && workoutKey) {
    if (selected) return <WorkoutDetail dataset={dataset} model={model} workout={selected} phone />;
    return (
      <>
        <NotFound text={s.workoutNotFound} />
        <Link href="/history" className="btn">
          {s.backToHistory}
        </Link>
      </>
    );
  }

  const p = panels(workouts, now, timeZone);
  const grid = dayGrid(workouts, now, timeZone);
  const stats: [string, string, string, boolean][] = [
    [s.panelThisWeek, String(p.thisWeek), s.subWorkouts, false],
    [s.panelThisMonth, String(p.thisMonth), s.subWorkouts, false],
    [s.panelTime, hoursMinutes(p.timeThisMonthMs), s.subHours, false],
    [s.panelBests, `▲ ${p.bestsThisMonth}`, s.subBests, true],
  ];

  return (
    <>
      <div className="history-top">
        {stats.map(([k, v, sub, good]) => (
          <div key={k} className="panel stat">
            <span className="label">{k}</span>
            <span className={"stat-value" + (good ? " success" : "")}>{v}</span>
            <span className="stat-sub">{sub}</span>
          </div>
        ))}
        {!phone && (
          <figure className="panel weeks">
            <figcaption className="label">
              <span>{s.weeks}</span>
              <span>{s.weeksSub}</span>
            </figcaption>
            <div className="weeks-grid" role="img" aria-label={s.weeksAria(grid.filter((d) => d.trained).length)}>
              {grid.map((d) => (
                <span key={d.day} title={d.isFuture ? undefined : d.day} className={(d.trained ? "on" : "") + (d.isToday ? " today" : "") + (d.isFuture ? " future" : "")} />
              ))}
            </div>
          </figure>
        )}
      </div>

      {workouts.length === 0 ? (
        <NothingSynced />
      ) : (
        <div className="history-main">
          <section className="history-list" aria-label={s.listAria}>
            {byMonth(workouts).map((group) => (
              <div key={group.month} style={{ display: "contents" }}>
                <div className="label">{monthHeading(group.month, language)}</div>
                {group.workouts.map((w) => (
                  <WorkoutRow key={w.id} workout={w} href={`/history/${model.workoutKey.get(w.id)}`} current={!phone && w.id === selected?.id} phone={phone} />
                ))}
              </div>
            ))}
          </section>
          {!phone && (selected ? <WorkoutDetail dataset={dataset} model={model} workout={selected} phone={false} /> : <NotFound text={s.workoutNotFound} />)}
        </div>
      )}
    </>
  );
}

function WorkoutRow({ workout: w, href, current, phone }: { workout: Workout; href: string; current: boolean; phone: boolean }) {
  return (
    <Link href={href} className="row workout-row" aria-current={current ? "true" : undefined}>
      {!phone && <span className="row-cursor">{current && <Cursor size={20} />}</span>}
      <span className="tile date-tile">
        <span className="dow">{weekdayTag(w.day, language)}</span>
        <span className="day">{Number(w.day.slice(8))}</span>
      </span>
      <span className="row-text">
        <span className="row-title">{w.routineName}</span>
        <span className="row-meta">
          {clock(w.durationMs)} · {s.sets(w.setCount)}
        </span>
      </span>
      {w.newBests.size > 0 && <span className="tag">{s.bests(w.newBests.size)}</span>}
      {phone && <Icon path={chevronIcon} size={20} color="var(--muted)" />}
    </Link>
  );
}

function WorkoutDetail({ dataset, model, workout: w, phone }: { dataset: Dataset; model: Model; workout: Workout; phone: boolean }) {
  const exercises = workoutExercises(dataset, w);
  const sub = `${phone ? shortDay(w.day, language) : longDay(w.day, language)} · ${clock(w.durationMs)} · ${s.sets(w.setCount)}`;
  return (
    <article className="panel workout" aria-labelledby="workout-title">
      {phone ? (
        <>
          <div className="workout-head">
            <Link href="/history" className="btn" aria-label={s.backToHistory} style={{ width: 48, height: 48, padding: 0 }}>
              <Icon path={backIcon} size={24} color="var(--action)" />
            </Link>
            <h1 id="workout-title">{w.routineName}</h1>
            <span />
          </div>
          <div className="muted" style={{ textAlign: "center", fontSize: 14 }}>
            {sub}
          </div>
        </>
      ) : (
        <div className="workout-head">
          <div>
            <div className="label">{s.workout}</div>
            <h1 id="workout-title">{w.routineName}</h1>
            <div className="muted">{sub}</div>
          </div>
          {w.newBests.size > 0 && (
            <span className="tag" style={{ fontSize: 15, padding: "4px 8px" }}>
              {s.newBestsTag(w.newBests.size)}
            </span>
          )}
        </div>
      )}
      <div className="workout-exercises">
        {exercises.map((e) => {
          const slug = model.exerciseSlug.get(e.exerciseId);
          return (
            <div key={e.exerciseId} className={"workout-exercise" + (e.isNewBest ? " best" : "")}>
              <div className="workout-exercise-name">
                {slug ? <Link href={`/progress/${slug}`}>{e.name}</Link> : <strong>{e.name}</strong>}
                {e.isNewBest ? <span className="small success">{s.newBestNote}</span> : e.template && <span className="small muted">{e.template}</span>}
              </div>
              <div className="chips">
                {e.sets.map((set) => (
                  <span key={set.setIndex} className={"chip" + (e.isNewBest && set.setIndex === 1 ? " best" : "")} title={set.note ?? undefined}>
                    <span className="chip-key">{s.setKey(set.setIndex)}</span>
                    <span className="chip-value">{set.reps}</span>
                    {set.weightKg != null && set.weightKg > 0 && <span className="chip-kg">{kgShort(set.weightKg, language)} kg</span>}
                  </span>
                ))}
              </div>
            </div>
          );
        })}
      </div>
      <div className="small muted">{s.readOnlyHistory}</div>
    </article>
  );
}

export function NothingSynced() {
  return (
    <div style={{ maxWidth: 520, display: "flex", flexDirection: "column", gap: 12 }}>
      <div className="textbox">{s.nothingSynced}</div>
      <GetTheApp />
    </div>
  );
}

export function GetTheApp() {
  return (
    <div className="signin-app">
      <a className="btn" href="https://github.com/Pierorivera1/DinoGym/releases/latest" style={{ minHeight: 48, fontSize: 16 }}>
        {s.getApp}
      </a>
      <span className="small muted">{s.getAppNote}</span>
    </div>
  );
}

export function NotFound({ text }: { text: string }) {
  return <p className="muted">{text}</p>;
}
