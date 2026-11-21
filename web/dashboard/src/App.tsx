import { useEffect, useMemo, useRef, useState } from "react";
import { ApiClient } from "./api/client";
import { SessionEndedError, SessionManager } from "./api/session";
import { emptyDataset, type Dataset } from "./data/dataset";
import { EXAMPLE_NAME, exampleDataset } from "./example";
import { pullInto } from "./data/load";
import { GOOGLE_CLIENT_ID, whenGoogleReady } from "./google";
import { language, strings as s } from "./i18n";
import { dayKey, dayNumber, localTimeZone } from "./rules/days";
import { parseRoute, routePath, type Route } from "./routes";
import { buildModel, displayName } from "./ui/model";
import { Cursor } from "./ui/Pixel";
import { useThemeMode, type ThemeMode } from "./ui/themeMode";
import { GetTheApp, HistoryView, NotFound } from "./views/HistoryView";
import { Link, navigate, usePathname, usePhone } from "./views/Link";
import { ProgressView } from "./views/ProgressView";
import { WeightView } from "./views/WeightView";

const api = new ApiClient();
const session = new SessionManager(api);

type SignedOutReason = "failed" | "ended" | undefined;

type State =
  | { kind: "signedOut"; reason: SignedOutReason }
  | { kind: "loading" }
  | { kind: "loadFailed" }
  | {
      kind: "loaded";
      dataset: Dataset;
      cursor: number;
      version: number;
      loadedAt: number;
      refreshing: boolean;
      refreshFailed: boolean;
      /** The made-up account from SEE AN EXAMPLE (decision 114): nothing is pulled or refreshed. */
      example: boolean;
    };

export function App() {
  const [state, setState] = useState<State>({ kind: "signedOut", reason: undefined });
  const [name, setName] = useState("");
  const [theme, toggleTheme] = useThemeMode();

  const load = async () => {
    setState({ kind: "loading" });
    const dataset = emptyDataset();
    try {
      const cursor = await pullInto(dataset, api, session, 0);
      setState({ kind: "loaded", dataset, cursor, version: 1, loadedAt: Date.now(), refreshing: false, refreshFailed: false, example: false });
    } catch (e) {
      setState(e instanceof SessionEndedError ? { kind: "signedOut", reason: "ended" } : { kind: "loadFailed" });
    }
  };

  // REFRESH pulls from the last cursor into the same rows (decision 110).
  const refresh = async () => {
    if (state.kind !== "loaded" || state.refreshing || state.example) return;
    const { dataset, cursor } = state;
    setState({ ...state, refreshing: true, refreshFailed: false });
    try {
      const next = await pullInto(dataset, api, session, cursor);
      setState((prev) => (prev.kind === "loaded" ? { ...prev, cursor: next, version: prev.version + 1, loadedAt: Date.now(), refreshing: false } : prev));
    } catch (e) {
      if (e instanceof SessionEndedError) setState({ kind: "signedOut", reason: "ended" });
      else setState((prev) => (prev.kind === "loaded" ? { ...prev, version: prev.version + 1, refreshing: false, refreshFailed: true } : prev));
    }
  };

  const signIn = (credential: string) => {
    setName(displayName(credential));
    setState({ kind: "loading" });
    session.signIn(credential).then(load, () => setState({ kind: "signedOut", reason: "failed" }));
  };

  const showExample = () => {
    setName(EXAMPLE_NAME);
    setState({ kind: "loaded", dataset: exampleDataset(language === "es"), cursor: 0, version: 1, loadedAt: Date.now(), refreshing: false, refreshFailed: false, example: true });
    navigate(routePath({ view: "history", workout: null }), true);
  };

  /** SIGN OUT, or SIGN IN from the example: back to the sign-in page. */
  const signOut = () => {
    if (state.kind === "loaded" && !state.example) {
      session.signOut();
      window.google?.accounts.id.disableAutoSelect();
    }
    setName("");
    setState({ kind: "signedOut", reason: undefined });
  };

  if (state.kind === "loaded") {
    return <Dashboard state={state} name={name} onRefresh={refresh} onSignOut={signOut} theme={theme} onToggleTheme={toggleTheme} />;
  }
  return <SignIn state={state} onCredential={signIn} onRetry={load} onExample={showExample} theme={theme} />;
}

// ---------- Signed out (WebSignIn.dc.html) ----------

/** NIGHT in Day, DAY in Night: the button names the theme it switches to (decision 113). */
function ThemeButton({ theme, onToggle }: { theme: ThemeMode; onToggle: () => void }) {
  const toNight = theme === "day";
  return (
    <button className="btn" onClick={onToggle} aria-label={toNight ? s.themeToNightAria : s.themeToDayAria}>
      {toNight ? s.themeNight : s.themeDay}
    </button>
  );
}

interface SignInProps {
  state: Exclude<State, { kind: "loaded" }>;
  onCredential: (c: string) => void;
  onRetry: () => void;
  onExample: () => void;
  /** The saved theme; the sign-in page has no switch (decision 114). */
  theme: ThemeMode;
}

function SignIn({ state, onCredential, onRetry, onExample, theme }: SignInProps) {
  const button = useRef<HTMLDivElement>(null);
  const signedOut = state.kind === "signedOut";
  const callback = useRef(onCredential);
  callback.current = onCredential;
  // One Tap is offered once per sign-out, not again when the theme redraws the button.
  const prompted = useRef(false);
  if (!signedOut) prompted.current = false;

  useEffect(() => {
    if (!signedOut) return;
    let cancelled = false;
    void whenGoogleReady().then((gis) => {
      if (cancelled || !button.current) return;
      gis.initialize({ client_id: GOOGLE_CLIENT_ID, auto_select: true, callback: ({ credential }) => callback.current(credential) });
      gis.renderButton(button.current, { theme: theme === "night" ? "filled_black" : "outline", size: "large", text: "signin_with", locale: language });
      if (!prompted.current) gis.prompt();
      prompted.current = true;
    });
    return () => {
      cancelled = true;
    };
  }, [signedOut, theme]);

  return (
    <main className="signin">
      <div className="signin-body">
        {state.kind === "signedOut" && state.reason === "ended" && <p className="muted" role="status" style={{ margin: 0 }}>{s.sessionEnded}</p>}
        <div className="signin-brand px">
          <Cursor size={64} />
          <span>DINOGYM</span>
        </div>
        <h1>{s.title}</h1>
        <div className="textbox">{s.intro}</div>
        {signedOut && (
          <>
            {state.reason === "failed" && <p className="danger" role="alert" style={{ margin: 0 }}>{s.signInFailed}</p>}
            <div ref={button} className="signin-google" />
            <div className="signin-example">
              <button className="btn" onClick={onExample}>
                {s.seeExample}
              </button>
              <span className="small muted">{s.seeExampleNote}</span>
            </div>
            <div className="small muted" style={{ lineHeight: 1.5 }}>
              {s.privacy}
            </div>
          </>
        )}
        {state.kind === "loading" && (
          <p className="muted" role="status" style={{ margin: 0 }}>
            {s.loading}
          </p>
        )}
        {state.kind === "loadFailed" && (
          <>
            <p className="danger" role="alert" style={{ margin: 0 }}>
              {s.loadFailed}
            </p>
            <button className="btn" style={{ minHeight: 48, fontSize: 16 }} onClick={onRetry}>
              {s.tryAgain}
            </button>
          </>
        )}
        <div className="signin-divider" />
        <GetTheApp />
      </div>
    </main>
  );
}

// ---------- Signed in ----------

type Loaded = Extract<State, { kind: "loaded" }>;

function useNow(intervalMs: number): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

interface DashboardProps {
  state: Loaded;
  name: string;
  onRefresh: () => void;
  onSignOut: () => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
}

function Dashboard({ state, name, onRefresh, onSignOut, theme, onToggleTheme }: DashboardProps) {
  const path = usePathname();
  const phone = usePhone();
  const now = useNow(30_000);
  const timeZone = useMemo(localTimeZone, []);
  const model = useMemo(() => buildModel(state.dataset, timeZone), [state.dataset, state.version, timeZone]);
  const route = parseRoute(path);
  const todayN = dayNumber(dayKey(now, timeZone));

  // "/" is HISTORY; the address bar shows it.
  useEffect(() => {
    if (path === "/") navigate(routePath({ view: "history", workout: null }), true);
  }, [path]);

  useEffect(() => {
    const view = route.view === "notFound" ? "" : ` · ${s.tabs[route.view]}`;
    document.title = `DinoGym${view}`;
  }, [route.view]);

  const minutes = Math.floor((now - state.loadedAt) / 60_000);
  const updated = state.refreshing ? s.refreshing : s.updated(Math.max(0, minutes));

  return (
    <div className="shell">
      <Header route={route} name={name} updated={updated} refreshing={state.refreshing} onRefresh={onRefresh} onSignOut={onSignOut} phone={phone} theme={theme} onToggleTheme={onToggleTheme} example={state.example} />
      {state.example && (
        <div className="textbox" role="note">
          {s.exampleBanner}
        </div>
      )}
      {state.refreshFailed && (
        <div className="alert" role="alert">
          <span>{s.refreshFailed}</span>
          <button className="btn" onClick={onRefresh}>
            {s.tryAgain}
          </button>
        </div>
      )}
      <main>
        <View route={route} state={state} model={model} phone={phone} now={now} todayN={todayN} timeZone={timeZone} />
      </main>
    </div>
  );
}

function View({ route, state, model, phone, now, todayN, timeZone }: { route: Route; state: Loaded; model: ReturnType<typeof buildModel>; phone: boolean; now: number; todayN: number; timeZone: string }) {
  switch (route.view) {
    case "history":
      return (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <HistoryView dataset={state.dataset} model={model} workoutKey={route.workout} phone={phone} now={now} timeZone={timeZone} />
        </div>
      );
    case "progress":
      return <ProgressView model={model} slug={route.exercise} phone={phone} todayN={todayN} timeZone={timeZone} />;
    case "weight":
      return <WeightView model={model} phone={phone} todayN={todayN} timeZone={timeZone} />;
    case "notFound":
      return <NotFound text={s.notFound} />;
  }
}

const TABS = ["history", "progress", "weight"] as const;

interface HeaderProps {
  route: Route;
  name: string;
  updated: string;
  refreshing: boolean;
  onRefresh: () => void;
  onSignOut: () => void;
  phone: boolean;
  theme: ThemeMode;
  onToggleTheme: () => void;
  example: boolean;
}

function Header({ route, name, updated, refreshing, onRefresh, onSignOut, phone, theme, onToggleTheme, example }: HeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const initial = (name.trim().charAt(0) || "?").toUpperCase();
  const hideTabs = phone && route.view === "history" && route.workout != null;

  const tabs = (
    <nav className="tabs" aria-label={s.navAria}>
      {TABS.map((tab) => (
        <Link key={tab} href={`/${tab}`} className="btn seg" aria-current={route.view === tab ? "page" : undefined}>
          {s.tabs[tab]}
        </Link>
      ))}
    </nav>
  );

  if (phone) {
    return (
      <>
        <header className="top" style={{ position: "relative" }}>
          <Link href="/history" className="brand px">
            <Cursor size={32} />
            <span>DINOGYM</span>
          </Link>
          <span className="top-spacer" />
          <button className="btn" aria-label={s.accountAria(name)} aria-expanded={menuOpen} onClick={() => setMenuOpen((o) => !o)} style={{ width: 48, height: 48, padding: 0, fontSize: 20 }}>
            {initial}
          </button>
          {menuOpen && (
            <div className="account-menu" onClick={() => setMenuOpen(false)}>
              <strong>{name}</strong>
              {example ? (
                <span className="tag" style={{ alignSelf: "flex-start" }}>{s.exampleTag}</span>
              ) : (
                <>
                  <span className="top-updated">{updated}</span>
                  <button className="btn" onClick={onRefresh} disabled={refreshing}>
                    {s.refresh}
                  </button>
                </>
              )}
              <ThemeButton theme={theme} onToggle={onToggleTheme} />
              <button className="btn" onClick={onSignOut}>
                {example ? s.signIn : s.signOut}
              </button>
            </div>
          )}
        </header>
        {!hideTabs && tabs}
      </>
    );
  }

  return (
    <header className="top">
      <Link href="/history" className="brand px">
        <Cursor size={36} />
        <span>DINOGYM</span>
      </Link>
      {tabs}
      <span className="top-spacer" />
      {example ? (
        <span className="tag">{s.exampleTag}</span>
      ) : (
        <>
          <span className="top-updated" role="status">
            {updated}
          </span>
          <button className="btn" onClick={onRefresh} disabled={refreshing}>
            {s.refresh}
          </button>
        </>
      )}
      <ThemeButton theme={theme} onToggle={onToggleTheme} />
      {name && (
        <span className="who">
          <span className="tile avatar" aria-hidden="true">
            {initial}
          </span>
          <span>{name}</span>
        </span>
      )}
      <button className="btn" onClick={onSignOut}>
        {example ? s.signIn : s.signOut}
      </button>
    </header>
  );
}
