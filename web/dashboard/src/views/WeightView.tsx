import { useState } from "react";
import { language, strings as s } from "../i18n";
import { currentAverage, goalProgress, isTowardGoal } from "../rules/bodyWeight";
import { dayKey, dayNumber } from "../rules/days";
import { StepChart } from "../ui/Chart";
import { kg, kgShort, monthTag, shortDay, signedKg } from "../ui/format";
import { initialRange, weightInRange, type Model, type RangeId } from "../ui/model";

const RANGES: RangeId[] = ["1M", "3M", "1Y", "ALL"];

interface Props {
  model: Model;
  phone: boolean;
  todayN: number;
  timeZone: string;
}

export function WeightView({ model, phone, todayN, timeZone }: Props) {
  const [range, setRange] = useState<RangeId>(() => {
    const oldest = model.weighIns[model.weighIns.length - 1];
    return initialRange(RANGES, oldest ? dayNumber(dayKey(oldest.measuredAt, timeZone)) : null, todayN, "3M");
  });
  if (model.weighIns.length === 0) {
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 12, maxWidth: 520 }}>
        <p className="muted">{s.noWeighIns}</p>
      </div>
    );
  }

  const r = weightInRange(model, range, todayN, timeZone);
  const current = currentAverage(model.averages);
  const goal = model.goalKg;
  const toward = goal != null && current != null ? goalProgress(current, goal) : null;
  const changeGood = r.change != null && toward != null && isTowardGoal(r.change, toward.direction);
  const rangeLabel = (id: RangeId) => s.weightRanges[RANGES.indexOf(id)] ?? id;
  const firstAvg = r.averages[0];
  const lastAvg = r.averages[r.averages.length - 1];

  const panels: [string, string, string, boolean][] = [
    [s.panelCurrent, current != null ? kg(current, language) : "–", s.subCurrent, false],
    [s.panelChange, r.change != null ? signedKg(r.change, language) : "–", s.subChange(range), changeGood],
    [
      s.panelGoal,
      goal != null ? kg(goal, language) : "–",
      goal == null ? s.subNoGoal : toward?.direction === "reached" ? s.subGoalReached : s.subGoalToGo(kg(toward?.remainingKg ?? 0, language)),
      false,
    ],
  ];

  return (
    <div className="weight-main">
      <div className="weight-left">
        <div className="stats3">
          {panels.map(([k, v, sub, good]) => (
            <div key={k} className="panel stat">
              <span className="label">{k}</span>
              <span className={"stat-value" + (good ? " success" : "")}>{v}</span>
              <span className="stat-sub">{sub}</span>
            </div>
          ))}
        </div>
        <figure className="panel chart weight-chart">
          <figcaption>
            <span className="label" style={{ fontSize: 13, flexGrow: 1 }}>
              {s.chartWeight}
            </span>
            <span className="legend">
              <span>
                <span className="key-line" />
                {s.legendAvg}
              </span>
              <span>
                <span className="key-dot" />
                {s.legendRaw}
              </span>
              {goal != null && (
                <span>
                  <span className="key-goal" />
                  {s.legendGoal}
                </span>
              )}
            </span>
            <span className="ranges" role="group" aria-label={s.rangeAria}>
              {RANGES.map((id) => (
                <button key={id} className="btn seg" aria-pressed={id === range} onClick={() => setRange(id)}>
                  {rangeLabel(id)}
                </button>
              ))}
            </span>
          </figcaption>
          <StepChart
            height={phone ? 240 : 470}
            xMin={r.xMin}
            xMax={r.xMax}
            dots={r.raw.map((p) => ({ x: p.x, y: p.value }))}
            steps={r.averages.map((p) => ({ x: p.x, y: p.value }))}
            tint={false}
            stepPoints={false}
            goal={goal}
            minStep={0.5}
            formatTick={(v) => kgShort(v, language)}
            monthLabel={(m) => monthTag(m, language)}
            label={firstAvg && lastAvg ? s.weightChartAria(kg(firstAvg.value, language), kg(lastAvg.value, language), goal != null ? kg(goal, language) : null) : s.chartWeight}
          />
        </figure>
      </div>

      <section className="weight-side" aria-labelledby="weigh-ins">
        <div className="label list-head" style={{ marginTop: 0 }}>
          <span id="weigh-ins">{s.weighIns}</span>
          <span>{s.newestFirst}</span>
        </div>
        <ul className="panel list weigh-ins">
          {model.weighIns.map((w) => (
            <li key={w.id}>
              <span style={{ flexGrow: 1 }}>{shortDay(dayKey(w.measuredAt, timeZone), language)}</span>
              <span className="kg">{kg(w.weightKg, language)}</span>
            </li>
          ))}
        </ul>
        <span className="small muted" style={{ lineHeight: 1.45 }}>
          {s.readOnlyWeight}
        </span>
      </section>
    </div>
  );
}
