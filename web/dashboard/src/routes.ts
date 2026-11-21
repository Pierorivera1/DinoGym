// The dashboard's URLs (decision 110): one per view, so a view can be bookmarked and
// the browser's back button works. No router library (decision 111).
//
//   /history                 the newest workout open (on a phone: the list)
//   /history/2026-11-16      the workout of that day; a second one that day is 2026-11-16-2
//   /progress                the most recently trained exercise
//   /progress/pull-ups       one exercise, by a slug of its name
//   /weight

export type Route =
  | { view: "history"; workout: string | null }
  | { view: "progress"; exercise: string | null }
  | { view: "weight" }
  | { view: "notFound" };

export function parseRoute(pathname: string): Route {
  const parts = pathname.split("/").filter(Boolean).map(decodeURIComponent);
  const [view, arg, ...rest] = parts;
  if (rest.length > 0) return { view: "notFound" };
  if (view === undefined) return { view: "history", workout: null };
  if (view === "history") return { view: "history", workout: arg ?? null };
  if (view === "progress") return { view: "progress", exercise: arg ?? null };
  if (view === "weight" && arg === undefined) return { view: "weight" };
  return { view: "notFound" };
}

export function routePath(route: Route): string {
  switch (route.view) {
    case "history":
      return route.workout ? `/history/${encodeURIComponent(route.workout)}` : "/history";
    case "progress":
      return route.exercise ? `/progress/${encodeURIComponent(route.exercise)}` : "/progress";
    case "weight":
      return "/weight";
    case "notFound":
      return "/";
  }
}

/** "Front lever + pulls" → "front-lever-pulls"; "Curl de bíceps" → "curl-de-biceps". */
export function slugify(name: string): string {
  const slug = name
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "exercise";
}

/**
 * A unique slug for each id, from its name. Ids are taken in the order given, so the
 * first exercise with a name keeps the plain slug and later ones get -2, -3.
 */
export function uniqueSlugs(items: { id: string; name: string }[]): Map<string, string> {
  const taken = new Set<string>();
  const result = new Map<string, string>();
  for (const item of items) {
    const base = slugify(item.name);
    let slug = base;
    for (let n = 2; taken.has(slug); n++) slug = `${base}-${n}`;
    taken.add(slug);
    result.set(item.id, slug);
  }
  return result;
}

/**
 * Keys for workouts: the day, then -2, -3 for later workouts the same day.
 * [workouts] is newest first, as the history list shows them.
 */
export function workoutKeys(workouts: { id: string; day: string; startedAt: number }[]): Map<string, string> {
  const byDay = new Map<string, { id: string; startedAt: number }[]>();
  for (const w of workouts) {
    const list = byDay.get(w.day);
    if (list) list.push(w);
    else byDay.set(w.day, [w]);
  }
  const result = new Map<string, string>();
  for (const [day, list] of byDay) {
    list.sort((a, b) => a.startedAt - b.startedAt);
    list.forEach((w, i) => result.set(w.id, i === 0 ? day : `${day}-${i + 1}`));
  }
  return result;
}
