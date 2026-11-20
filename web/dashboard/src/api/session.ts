import { ApiError, type ApiClient } from "./client";
import type { Session } from "./types";

/** The session could not be refreshed and is gone; the user signs in again. */
export class SessionEndedError extends Error {}

/** The browser is offline or the API throttled us; the session is kept. */
export class OfflineError extends Error {}

const REFRESH_EARLY_MS = 60_000;

/**
 * Keeps the dashboard's session in memory only (decision 111): closing the tab
 * signs out, and nothing a script could steal is left in browser storage.
 *
 * Refresh tokens are single use and a reused one revokes the whole family
 * (decision 79), so one refresh runs at a time and a refresh is never retried.
 * When a refresh fails the session ends, except when the browser is offline or
 * API Gateway throttled the call (429): neither reached the backend, so the
 * token is unspent. This is decision 83's rule with the browser's coarser view
 * of network errors.
 */
export class SessionManager {
  private session: Session | null = null;
  private refreshing: Promise<Session> | null = null;

  constructor(
    private readonly api: ApiClient,
    private readonly now: () => number = () => Date.now(),
    private readonly isOnline: () => boolean = () => navigator.onLine,
  ) {}

  get signedIn(): boolean {
    return this.session !== null;
  }

  async signIn(googleIdToken: string): Promise<void> {
    this.session = await this.api.signInWithGoogle(googleIdToken);
  }

  /** Local only: the refresh token expires on the server after 60 days (decision 83). */
  signOut(): void {
    this.session = null;
    this.refreshing = null;
  }

  /** Runs [call] with a fresh access token; on a 401 refreshes once and retries. */
  async authorized<T>(call: (accessToken: string) => Promise<T>): Promise<T> {
    const token = await this.accessToken();
    try {
      return await call(token);
    } catch (e) {
      if (!(e instanceof ApiError) || e.status !== 401) throw e;
    }
    const retried = await this.refresh();
    try {
      return await call(retried.accessToken);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) this.end();
      throw e instanceof ApiError && e.status === 401 ? new SessionEndedError() : e;
    }
  }

  private async accessToken(): Promise<string> {
    const session = this.current();
    if (session.accessTokenExpiresAt - this.now() > REFRESH_EARLY_MS) return session.accessToken;
    return (await this.refresh()).accessToken;
  }

  private refresh(): Promise<Session> {
    if (this.refreshing) return this.refreshing;
    const sent = this.current();
    const attempt = this.api.refresh(sent.refreshToken).then(
      (fresh) => {
        if (this.session === sent) this.session = fresh;
        return fresh;
      },
      (e: unknown) => {
        if (!this.isOnline() || (e instanceof ApiError && e.status === 429)) throw new OfflineError();
        if (this.session === sent) this.end();
        throw new SessionEndedError();
      },
    );
    this.refreshing = attempt;
    attempt.then(
      () => this.clearRefreshing(attempt),
      () => this.clearRefreshing(attempt),
    );
    return attempt;
  }

  private clearRefreshing(attempt: Promise<Session>): void {
    if (this.refreshing === attempt) this.refreshing = null;
  }

  private current(): Session {
    if (!this.session) throw new SessionEndedError();
    return this.session;
  }

  private end(): void {
    this.session = null;
  }
}
