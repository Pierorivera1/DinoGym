// Calendar days in a time zone, as "YYYY-MM-DD" keys. The dashboard uses the
// browser's zone, as the app uses the phone's.

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(timeZone: string): Intl.DateTimeFormat {
  let f = formatters.get(timeZone);
  if (!f) {
    f = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" });
    formatters.set(timeZone, f);
  }
  return f;
}

export function localTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/** The day [epochMs] falls on in [timeZone]. */
export function dayKey(epochMs: number, timeZone: string): string {
  return formatter(timeZone).format(epochMs);
}

/** Days since 1970-01-01 for a day key; differences between days are exact. */
export function dayNumber(key: string): number {
  const [y, m, d] = key.split("-").map(Number) as [number, number, number];
  return Date.UTC(y, m - 1, d) / 86_400_000;
}

export function keyOfDayNumber(n: number): string {
  return new Date(n * 86_400_000).toISOString().slice(0, 10);
}

/** 0 = Monday … 6 = Sunday. */
export function weekdayMondayFirst(key: string): number {
  return (new Date(dayNumber(key) * 86_400_000).getUTCDay() + 6) % 7;
}

/** "YYYY-MM" of a day key. */
export function monthKey(key: string): string {
  return key.slice(0, 7);
}
