import { useEffect, useRef, useState } from "react";
import { ApiClient } from "./api/client";
import { SessionEndedError, SessionManager } from "./api/session";
import { emptyDataset, type Dataset } from "./data/dataset";
import { pullInto } from "./data/load";
import { GOOGLE_CLIENT_ID, whenGoogleReady } from "./google";
import { language, strings as s } from "./i18n";
import { finishedWorkouts } from "./rules/history";
import { localTimeZone } from "./rules/days";

const api = new ApiClient();
const session = new SessionManager(api);

type State =
  | { kind: "signedOut"; message?: string }
  | { kind: "loading" }
  | { kind: "loaded"; dataset: Dataset; cursor: number }
  | { kind: "loadFailed" };

// A first, plain screen to prove sign-in and the pull end to end (task 25, PR 1).
// The approved views replace it in the next pull request.
export function App() {
  const [state, setState] = useState<State>({ kind: "signedOut" });
  const button = useRef<HTMLDivElement>(null);

  const load = async () => {
    setState({ kind: "loading" });
    const dataset = emptyDataset();
    try {
      const cursor = await pullInto(dataset, api, session, 0);
      setState({ kind: "loaded", dataset, cursor });
    } catch (e) {
      setState(e instanceof SessionEndedError ? { kind: "signedOut", message: s.sessionEnded } : { kind: "loadFailed" });
    }
  };

  useEffect(() => {
    if (state.kind !== "signedOut") return;
    let cancelled = false;
    void whenGoogleReady().then((gis) => {
      if (cancelled || !button.current) return;
      gis.initialize({
        client_id: GOOGLE_CLIENT_ID,
        auto_select: true,
        callback: ({ credential }) => {
          session.signIn(credential).then(load, () => setState({ kind: "signedOut", message: s.signInFailed }));
        },
      });
      gis.renderButton(button.current, { theme: "outline", size: "large", text: "signin_with", locale: language });
      gis.prompt();
    });
    return () => {
      cancelled = true;
    };
  }, [state.kind]);

  const signOut = () => {
    session.signOut();
    window.google?.accounts.id.disableAutoSelect();
    setState({ kind: "signedOut" });
  };

  return (
    <main style={{ maxWidth: 480, margin: "48px auto", padding: "0 16px", fontFamily: "system-ui, sans-serif", lineHeight: 1.5 }}>
      <h1>DINOGYM</h1>
      {state.kind === "signedOut" && (
        <>
          {state.message && <p role="alert">{state.message}</p>}
          <p>{s.title}</p>
          <p>{s.intro}</p>
          <div ref={button} />
          <p>{s.privacy}</p>
        </>
      )}
      {state.kind === "loading" && <p>{s.loading}</p>}
      {state.kind === "loadFailed" && (
        <>
          <p role="alert">{s.loadFailed}</p>
          <button onClick={load}>{s.tryAgain}</button>
        </>
      )}
      {state.kind === "loaded" && (
        <>
          <p>{s.loaded(finishedWorkouts(state.dataset, localTimeZone()).length, state.dataset.weighIns.size)}</p>
          <button onClick={signOut}>{s.signOut}</button>
        </>
      )}
    </main>
  );
}
