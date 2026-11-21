import { useState } from "react";
import { language, strings as s } from "../i18n";
import { dayKey, dayNumber } from "../rules/days";
import type { Gain, SetOneResult } from "../rules/progress";
import { StepChart } from "../ui/Chart";
import { dayMonth, kgShort, monthTag, shortDay } from "../ui/format";
import { initialRange, progressInRange, type Model, type ProgressExercise, type RangeId } from "../ui/model";
import { Cursor, exerciseIcon, Icon } from "../ui/Pixel";
import { Link, navigate } from "./Link";

const RANGES: RangeId[] = ["3M", "6M", "1Y", "ALL"];

interface Props {
  model: Model;
  slug: string | null;
  phone: boolean;
  todayN: number;
  timeZone: string;
}

/** "7", or "12 kg × 10" when the exercise uses weight (the app's progress list). */
export function bestText(result: SetOneResult | null, usesWeight: boolean): string {
  if (!result) return "–";
  return usesWeight ? `${kgShort(result.weightKg ?? 0, language)} kg × ${result.reps}` : String(result.reps);
}

function gainText(gain: Gain): string {
  if (gain.kind === "kg") return `+${kgShort(gain.kg, language)} kg`;
  return gain.reps > 0 ? `+${gain.reps}` : String(gain.reps);
}

export function ProgressView({ model, slug, phone, todayN, timeZone }: Props) {
  const [query, setQuery] = useState("");
  const { exercises } = model;
  const [range, setRange] = useState<RangeId>(() => {
    const first = Math.min(...exercises.map((e) => e.history[0]?.date ?? Infinity));
    return initialRange(RANGES, Number.isFinite(first) ? dayNumber(dayKey(first, timeZone)) : null, todayN, "6M");
  });
  if (exercises.length === 0) return <p className="muted">{s.noProgress}</p>;

  const selected = (slug && exercises.find((e) => e.slug === slug)) || (slug ? undefined : exercises[0]);
  const needle = query.trim().toLocaleLowerCase();
  const shown = needle ? exercises.filter((e) => e.name.toLocaleLowerCase().includes(needle)) : exercises;

  const picker = phone ? (
    <label className="label" style={{ display: "flex", flexDirection: "column", gap: 6, fontSize: 13 }}>
      {s.exercise}
      <select className="field" style={{ height: 52 }} value={selected?.slug ?? ""} onChange={(e) => navigate(`/progress/${e.target.value}`)}>
        {exercises.map((e) => (
          <option key={e.id} value={e.slug}>
            {e.name} · {s.bestPrefix} {bestText(e.progress.best, e.progress.usesWeight)}
          </option>
        ))}
      </select>
    </label>
  ) : (
    <section className="exercise-list" aria-label={s.exercises}>
      <label className="label" style={{ fontSize: 13 }}>
        {s.search}
        <input className="field" type="search" placeholder={s.searchHint} value={query} onChange={(e) => setQuery(e.target.value)} />
      </label>
      <div className="label list-head">
        <span>{s.exercises}</span>
        <span>{s.bestHead}</span>
      </div>
      <div className="exercise-rows">
        {shown.length === 0 && <p className="muted small">{s.noMatch}</p>}
        {shown.map((e) => {
          const current = e.id === selected?.id;
          return (
            <Link key={e.id} href={`/progress/${e.slug}`} className="row" aria-current={current ? "true" : undefined}>
              <span className="row-cursor">{current && <Cursor size={20} />}</span>
              <span className="row-title">{e.name}</span>
              <span className="exercise-best">{bestText(e.progress.best, e.progress.usesWeight)}</span>
            </Link>
          );
        })}
      </div>
    </section>
  );

  return (
    <div className="progress-main">
      {picker}
      {selected ? (
        <ExerciseDetail exercise={selected} model={model} range={range} setRange={setRange} phone={phone} todayN={todayN} timeZone={timeZone} />
      ) : (
        <p className="muted">{s.notFound}</p>
      )}
    </div>
  );
}

interface DetailProps {
  exercise: ProgressExercise;
  model: Model;
  range: RangeId;
  setRange: (r: RangeId) => void;
  phone: boolean;
  todayN: number;
  timeZone: string;
}

function ExerciseDetail({ exercise: e, model, range, setRange, phone, todayN, timeZone }: DetailProps) {
  const r = progressInRange(e, range, todayN, timeZone);
  const usesWeight = e.progress.usesWeight;
  const unit = usesWeight ? s.unitKg : s.unitReps;
  const rangeLabel = (id: RangeId) => s.progressRanges[RANGES.indexOf(id)] ?? id;
  const recordsAria = [...r.records].reverse().map((rec) => `${bestText(rec.result, usesWeight)} · ${dayMonth(rec.day, language)}`).join(", ");

  const panels: [string, string, string, boolean][] = [
    [s.panelBest, bestText(e.progress.best, usesWeight), usesWeight ? s.subBestKg : s.subBestReps, false],
    [s.panelGain, gainText(r.gain), r.gainSince ? s.subGainSince(dayMonth(r.gainSince, language)) : "", true],
    [s.panelRecords, String(r.records.length), s.subRecordsIn(range), false],
  ];

  return (
    <article className="progress-detail">
      <div className="progress-head">
        {!phone && (
          <div className="tile" style={{ width: 56, height: 56 }}>
            <Icon path={exerciseIcon(e.iconKey)} size={48} color="var(--outline)" />
          </div>
        )}
        <div>
          {!phone && <h1>{e.name}</h1>}
          {!phone && <div className="muted" style={{ fontSize: 14 }}>{s.exSub(unit, e.routines.join(", "))}</div>}
        </div>
        <div className="ranges" role="group" aria-label={s.rangeAria}>
          {RANGES.map((id) => (
            <button key={id} className="btn seg" aria-pressed={id === range} onClick={() => setRange(id)}>
              {rangeLabel(id)}
            </button>
          ))}
        </div>
      </div>

      <div className="progress-charts">
        <figure className="panel chart">
          <figcaption>
            <span className="label" style={{ fontSize: 13 }}>
              {s.chartSetOne}
              {phone ? ` · ${rangeLabel(range)}` : ""}
            </span>
            <span className="legend">
              <span>
                <span className="key-line" />
                {s.legendBest}
              </span>
              <span>
                <span className="key-dot" />
                {s.legendSession}
              </span>
            </span>
          </figcaption>
          <StepChart
            height={phone ? 200 : 300}
            xMin={r.xMin}
            xMax={r.xMax}
            dots={r.sessions.map((p) => ({ x: p.x, y: p.value }))}
            steps={r.steps.map((p) => ({ x: p.x, y: p.value }))}
            lead={r.lead ? { x: r.lead.x, y: r.lead.value } : null}
            tint
            stepPoints
            minStep={1}
            formatTick={(v) => kgShort(v, language)}
            monthLabel={(m) => monthTag(m, language)}
            label={s.progressChartAria(e.name, recordsAria)}
          />
        </figure>
        <div className="side-stats">
          {panels.map(([k, v, sub, good]) => (
            <div key={k} className="panel stat">
              <span className="label">{k}</span>
              <span className={"stat-value" + (good ? " success" : "")}>{v}</span>
              <span className="stat-sub">{sub}</span>
            </div>
          ))}
        </div>
      </div>

      {r.records.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <div className="label list-head" style={{ marginTop: 0 }}>
            <span>{s.recordsLabel}</span>
            <span>{s.newestFirst}</span>
          </div>
          <ul className="panel list records">
            {r.records.map((rec, i) => (
              <li key={rec.result.sessionId} className={i === 0 ? "newest" : undefined}>
                <span className="date">{shortDay(rec.day, language)}</span>
                <span className="routine">{model.routineNameBySession.get(rec.result.sessionId)}</span>
                <span className="value">{bestText(rec.result, usesWeight)}</span>
                <span className={"delta " + (rec.gain ? "success" : "muted")}>{rec.gain ? `▲ ${gainText(rec.gain)}` : s.first}</span>
              </li>
            ))}
          </ul>
          <span className="small muted">{s.rule}</span>
        </div>
      )}
    </article>
  );
}
