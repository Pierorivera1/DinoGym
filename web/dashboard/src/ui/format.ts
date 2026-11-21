// Numbers, durations and dates in the browser's language (en or es) and time zone.

import type { Language } from "../i18n";
import { dayNumber } from "../rules/days";

const locales: Record<Language, string> = { en: "en-GB", es: "es-ES" };

/** 42:10 for 42 minutes 10 seconds; 1:02:10 past an hour (the app's summary format). */
export function clock(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = h > 0 ? String(m).padStart(2, "0") : String(m);
  return (h > 0 ? `${h}:` : "") + `${mm}:${String(s).padStart(2, "0")}`;
}

/** 6:42 for 6 hours 42 minutes (TIME THIS MONTH). */
export function hoursMinutes(ms: number): string {
  const minutes = Math.max(0, Math.round(ms / 60_000));
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, "0")}`;
}

/** One decimal, with the language's separator: 72.4 or 72,4. */
export function kg(value: number, language: Language): string {
  return new Intl.NumberFormat(locales[language], { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
}

/** A weight without trailing zeros: 12 or 12.5 (12,5). */
export function kgShort(value: number, language: Language): string {
  return new Intl.NumberFormat(locales[language], { maximumFractionDigits: 2 }).format(value);
}

/** A signed change with a real minus sign: −1.6, +0.4, 0.0. */
export function signedKg(value: number, language: Language): string {
  const rounded = Math.round(value * 10) / 10;
  const text = kg(Math.abs(rounded), language);
  return rounded > 0 ? `+${text}` : rounded < 0 ? `−${text}` : text;
}

function dayDate(key: string): Date {
  return new Date(dayNumber(key) * 86_400_000);
}

function formatDay(key: string, language: Language, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(locales[language], { ...options, timeZone: "UTC" }).format(dayDate(key));
}

/** "Monday 16 November" / "lunes 16 de noviembre". */
export function longDay(key: string, language: Language): string {
  return formatDay(key, language, { weekday: "long", day: "numeric", month: "long" }).replace(",", "");
}

/** "Mon 16 Nov" / "lun 16 nov". */
export function shortDay(key: string, language: Language): string {
  return formatDay(key, language, { weekday: "short", day: "numeric", month: "short" }).replace(",", "").replace(/\./g, "");
}

/** "7 July" / "7 de julio". */
export function dayMonth(key: string, language: Language): string {
  return formatDay(key, language, { day: "numeric", month: "long" });
}

/** "MON" / "LUN" for the date tile. */
export function weekdayTag(key: string, language: Language): string {
  return formatDay(key, language, { weekday: "short" }).replace(/\./g, "").toUpperCase();
}

/** "NOVEMBER 2026" / "NOVIEMBRE 2026" for a "YYYY-MM" month key. */
export function monthHeading(month: string, language: Language): string {
  return formatDay(`${month}-01`, language, { month: "long", year: "numeric" }).replace(" de ", " ").toUpperCase();
}

/** "JUN" / "JUN" for chart axes. */
export function monthTag(month: string, language: Language): string {
  return formatDay(`${month}-01`, language, { month: "short" }).replace(/\./g, "").toUpperCase().slice(0, 3);
}
