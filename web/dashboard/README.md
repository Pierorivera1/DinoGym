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

Google sign-in works only on the origins listed on the Web OAuth client
(`localhost:5173`, staging and prod). To look at the views without signing in, press
SEE AN EXAMPLE on the sign-in page: two months of made-up workouts, built in the
browser (`src/example.ts`, decision 114).

- `src/api/`: the generated contract types, the HTTP client and the in-memory session.
- `src/data/`: pulling every row through `/sync/pull` into an in-memory dataset.
- `src/rules/`: the app's progress, body weight and history rules, ported from Kotlin with their tests.
- `src/ui/`: the theme (`theme.css`), what each view shows (`model.ts`), the chart geometry, formatting and pixel icons.
- `src/views/`: HISTORY, PROGRESS and WEIGHT, and history-API navigation (`Link.tsx`). URLs are in `src/routes.ts`.
- `src/example.ts`: the example account behind SEE AN EXAMPLE.
