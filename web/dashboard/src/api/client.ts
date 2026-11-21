import type { PullResponse, Session } from "./types";

/** The request did not get an answer: offline, DNS, or the connection dropped. */
export class NetworkError extends Error {}

/** The API answered with an error status. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super(`API error ${status} ${code}`);
  }
}

export type Fetch = typeof fetch;

/**
 * The calls the dashboard makes. Paths are relative: in production CloudFront
 * sends them to the API, in development Vite's proxy does (decision 111).
 */
export class ApiClient {
  constructor(private readonly fetchFn: Fetch = (input, init) => fetch(input, init)) {}

  signInWithGoogle(idToken: string): Promise<Session> {
    return this.post("/auth/google", { idToken });
  }

  refresh(refreshToken: string): Promise<Session> {
    return this.post("/auth/refresh", { refreshToken });
  }

  pull(accessToken: string, since: number, limit = 1000): Promise<PullResponse> {
    return this.send(`/sync/pull?since=${since}&limit=${limit}`, {
      headers: { Accept: "application/json", Authorization: `Bearer ${accessToken}` },
    });
  }

  private post<T>(path: string, body: unknown): Promise<T> {
    return this.send(path, {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  }

  private async send<T>(path: string, init: RequestInit): Promise<T> {
    let response: Response;
    try {
      response = await this.fetchFn(path, init);
    } catch (e) {
      throw new NetworkError(e instanceof Error ? e.message : String(e));
    }
    if (!response.ok) {
      let code = "unknown";
      try {
        code = ((await response.json()) as { code?: string }).code ?? code;
      } catch {
        // API Gateway's own errors (429, 503) are not our Error shape.
      }
      throw new ApiError(response.status, code);
    }
    return (await response.json()) as T;
  }
}
