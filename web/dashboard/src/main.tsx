import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./ui/theme.css";
import { applyTheme, storedTheme } from "./ui/themeMode";

// Before the first render, so a saved Night does not flash Day first.
applyTheme(storedTheme());
// A page the browser restores from its back/forward cache keeps its old state; re-read
// the saved theme so Back never shows a theme the person did not choose.
window.addEventListener("pageshow", (e) => {
  if (e.persisted) applyTheme(storedTheme());
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
