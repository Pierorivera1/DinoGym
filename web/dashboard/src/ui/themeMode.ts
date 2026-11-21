import { useState } from "react";

// Day is the default; NIGHT / DAY in the header switches (decision 113). The choice is
// remembered in this browser only. Storage can be missing or blocked (private windows),
// so every access is guarded and the page still works, starting in Day.

export type ThemeMode = "day" | "night";

const KEY = "dinogym.theme";

export function storedTheme(): ThemeMode {
  try {
    return localStorage.getItem(KEY) === "night" ? "night" : "day";
  } catch {
    return "day";
  }
}

export function applyTheme(mode: ThemeMode): void {
  if (mode === "night") document.documentElement.dataset.theme = "dark";
  else delete document.documentElement.dataset.theme;
}

export function useThemeMode(): [ThemeMode, () => void] {
  const [mode, setMode] = useState<ThemeMode>(storedTheme);
  const toggle = () => {
    const next: ThemeMode = mode === "day" ? "night" : "day";
    applyTheme(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      // Not remembered; it still applies to this page.
    }
    setMode(next);
  };
  return [mode, toggle];
}
