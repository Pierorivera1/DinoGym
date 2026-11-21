import { describe, expect, it } from "vitest";
import { clock, dayMonth, hoursMinutes, kg, longDay, monthHeading, shortDay, signedKg, weekdayTag } from "./format";

describe("format", () => {
  it("formats durations like the app", () => {
    expect(clock((42 * 60 + 10) * 1000)).toBe("42:10");
    expect(clock((62 * 60 + 5) * 1000)).toBe("1:02:05");
    expect(hoursMinutes((6 * 60 + 42) * 60_000)).toBe("6:42");
  });

  it("uses each language's decimal separator and a real minus", () => {
    expect(kg(72.44, "en")).toBe("72.4");
    expect(kg(72.44, "es")).toBe("72,4");
    expect(signedKg(-1.6, "en")).toBe("−1.6");
    expect(signedKg(0.4, "es")).toBe("+0,4");
    expect(signedKg(0.01, "en")).toBe("0.0");
  });

  it("writes days in English and Spanish, whatever the browser's zone", () => {
    expect(longDay("2026-11-16", "en")).toBe("Monday 16 November");
    expect(longDay("2026-11-16", "es")).toBe("lunes 16 de noviembre");
    expect(shortDay("2026-11-16", "en")).toBe("Mon 16 Nov");
    expect(shortDay("2026-11-16", "es")).toBe("lun 16 nov");
    expect(dayMonth("2026-07-07", "en")).toBe("7 July");
    expect(dayMonth("2026-07-07", "es")).toBe("7 de julio");
    expect(weekdayTag("2026-11-14", "en")).toBe("SAT");
    expect(weekdayTag("2026-11-11", "es")).toBe("MIÉ");
    expect(monthHeading("2026-11", "en")).toBe("NOVEMBER 2026");
    expect(monthHeading("2026-11", "es")).toBe("NOVIEMBRE 2026");
  });
});
