import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./ui/theme.css";
import { applyTheme, storedTheme } from "./ui/themeMode";

// Before the first render, so a saved Night does not flash Day first.
applyTheme(storedTheme());

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
