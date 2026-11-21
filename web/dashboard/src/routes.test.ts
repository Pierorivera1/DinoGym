import { describe, expect, it } from "vitest";
import { parseRoute, routePath, slugify, uniqueSlugs, workoutKeys } from "./routes";

describe("parseRoute", () => {
  it("reads every view and its argument", () => {
    expect(parseRoute("/")).toEqual({ view: "history", workout: null });
    expect(parseRoute("/history")).toEqual({ view: "history", workout: null });
    expect(parseRoute("/history/2026-11-16-2")).toEqual({ view: "history", workout: "2026-11-16-2" });
    expect(parseRoute("/progress/pull-ups/")).toEqual({ view: "progress", exercise: "pull-ups" });
    expect(parseRoute("/weight")).toEqual({ view: "weight" });
  });

  it("rejects unknown paths", () => {
    expect(parseRoute("/r/abc")).toEqual({ view: "notFound" });
    expect(parseRoute("/weight/x")).toEqual({ view: "notFound" });
    expect(parseRoute("/history/a/b")).toEqual({ view: "notFound" });
  });

  it("round-trips through routePath", () => {
    for (const path of ["/history", "/history/2026-11-16", "/progress", "/progress/curl-de-biceps", "/weight"]) {
      expect(routePath(parseRoute(path))).toBe(path);
    }
  });
});

describe("slugs", () => {
  it("drops accents and symbols", () => {
    expect(slugify("Front lever + pulls")).toBe("front-lever-pulls");
    expect(slugify("Curl de bíceps")).toBe("curl-de-biceps");
    expect(slugify("  ¡Sentadilla!  ")).toBe("sentadilla");
    expect(slugify("+++")).toBe("exercise");
  });

  it("numbers repeated names in the order given", () => {
    const slugs = uniqueSlugs([
      { id: "a", name: "Rows" },
      { id: "b", name: "rows" },
      { id: "c", name: "Rows!" },
    ]);
    expect([...slugs.values()]).toEqual(["rows", "rows-2", "rows-3"]);
  });
});

describe("workoutKeys", () => {
  it("keys a day's first workout by the day and later ones with a number", () => {
    const keys = workoutKeys([
      { id: "late", day: "2026-11-16", startedAt: 2000 },
      { id: "early", day: "2026-11-16", startedAt: 1000 },
      { id: "other", day: "2026-11-14", startedAt: 500 },
    ]);
    expect(keys.get("early")).toBe("2026-11-16");
    expect(keys.get("late")).toBe("2026-11-16-2");
    expect(keys.get("other")).toBe("2026-11-14");
  });
});
