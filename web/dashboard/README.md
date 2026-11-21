# Web dashboard

The signed-in dashboard of task 25: history, progress and body weight, read-only
(decisions 110 and 111). React and TypeScript, built with Vite.

```bash
cd web/dashboard
npm ci            # once, and after package-lock.json changes
npm run dev       # http://localhost:5173, API calls proxied to staging
npm test          # unit tests (Vitest)
npm run build     # type-check, then build into dist/
npm run gen:api   # after api/openapi.yaml changes; commit src/api/schema.d.ts
```

`DINOGYM_API_URL=http://localhost:8080 npm run dev` points the proxy at the API
running on the laptop instead of staging.

To look at the views without signing in, open http://localhost:5173/?demo (a made-up
account; `?demo=empty` for one with nothing synced). It only exists in `npm run dev`. Google sign-in works only on the
origins listed on the Web OAuth client (`localhost:5173`, staging and prod).

- `src/api/`: the generated contract types, the HTTP client and the in-memory session.
- `src/data/`: pulling every row through `/sync/pull` into an in-memory dataset.
- `src/rules/`: the app's progress, body weight and history rules, ported from Kotlin with their tests.
- `src/ui/`: the theme (`theme.css`), what each view shows (`model.ts`), the chart geometry, formatting and pixel icons.
- `src/views/`: HISTORY, PROGRESS and WEIGHT, and history-API navigation (`Link.tsx`). URLs are in `src/routes.ts`.
- `src/dev/`: the demo account, left out of production builds.
