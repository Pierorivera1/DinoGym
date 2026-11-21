import { afterEach, describe, expect, it, vi } from "vitest";
import { applyTheme, storedTheme } from "./themeMode";

describe("theme mode", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("starts in Day unless Night was saved", () => {
    vi.stubGlobal("localStorage", { getItem: () => null });
    expect(storedTheme()).toBe("day");
    vi.stubGlobal("localStorage", { getItem: () => "night" });
    expect(storedTheme()).toBe("night");
  });

  it("falls back to Day when storage is blocked", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("blocked");
      },
    });
    expect(storedTheme()).toBe("day");
  });

  it("marks Night on the root element and clears it for Day", () => {
    const root = { dataset: {} as Record<string, string> };
    vi.stubGlobal("document", { documentElement: root });
    applyTheme("night");
    expect(root.dataset.theme).toBe("dark");
    applyTheme("day");
    expect(root.dataset.theme).toBeUndefined();
  });
});
