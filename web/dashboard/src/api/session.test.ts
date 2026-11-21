import { describe, expect, it, vi } from "vitest";
import { ApiClient, ApiError, NetworkError } from "./client";
import { OfflineError, SessionEndedError, SessionManager } from "./session";
import type { Session } from "./types";

const session = (n: number, expiresAt: number): Session => ({
  userId: "u",
  accessToken: `access-${n}`,
  accessTokenExpiresAt: expiresAt,
  refreshToken: `refresh-${n}`,
});

function setup(opts: { now?: number; online?: boolean } = {}) {
  const api = new ApiClient();
  const clock = { now: opts.now ?? 0 };
  const online = { value: opts.online ?? true };
  vi.spyOn(api, "signInWithGoogle").mockResolvedValue(session(1, 15 * 60_000));
  const manager = new SessionManager(api, () => clock.now, () => online.value);
  return { api, manager, clock, online };
}

describe("SessionManager", () => {
  it("uses the access token while it is fresh", async () => {
    const { manager } = setup();
    await manager.signIn("google");
    expect(await manager.authorized(async (t) => t)).toBe("access-1");
  });

  it("refreshes a minute before expiry, once for concurrent callers", async () => {
    const { api, manager, clock } = setup();
    await manager.signIn("google");
    clock.now = 14.5 * 60_000;
    const refresh = vi.spyOn(api, "refresh").mockResolvedValue(session(2, 30 * 60_000));
    const tokens = await Promise.all([manager.authorized(async (t) => t), manager.authorized(async (t) => t)]);
    expect(tokens).toEqual(["access-2", "access-2"]);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(refresh).toHaveBeenCalledWith("refresh-1");
  });

  it("refreshes once on a 401 and retries", async () => {
    const { api, manager } = setup();
    await manager.signIn("google");
    vi.spyOn(api, "refresh").mockResolvedValue(session(2, 30 * 60_000));
    const call = vi.fn(async (t: string) => {
      if (t === "access-1") throw new ApiError(401, "invalid_token");
      return "ok";
    });
    expect(await manager.authorized(call)).toBe("ok");
    expect(call).toHaveBeenCalledTimes(2);
  });

  it("ends the session when a refresh fails online, and never retries it", async () => {
    const { api, manager, clock } = setup();
    await manager.signIn("google");
    clock.now = 15 * 60_000;
    const refresh = vi.spyOn(api, "refresh").mockRejectedValue(new ApiError(401, "invalid_token"));
    await expect(manager.authorized(async (t) => t)).rejects.toBeInstanceOf(SessionEndedError);
    expect(manager.signedIn).toBe(false);
    await expect(manager.authorized(async (t) => t)).rejects.toBeInstanceOf(SessionEndedError);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("keeps the session when offline or throttled", async () => {
    const { api, manager, clock, online } = setup();
    await manager.signIn("google");
    clock.now = 15 * 60_000;
    online.value = false;
    vi.spyOn(api, "refresh").mockRejectedValueOnce(new NetworkError("offline")).mockRejectedValueOnce(new ApiError(429, "unknown"));
    await expect(manager.authorized(async (t) => t)).rejects.toBeInstanceOf(OfflineError);
    online.value = true;
    await expect(manager.authorized(async (t) => t)).rejects.toBeInstanceOf(OfflineError);
    expect(manager.signedIn).toBe(true);
  });

  it("ends the session when the retried call is still unauthorized", async () => {
    const { api, manager } = setup();
    await manager.signIn("google");
    vi.spyOn(api, "refresh").mockResolvedValue(session(2, 30 * 60_000));
    await expect(
      manager.authorized(async () => {
        throw new ApiError(401, "invalid_token");
      }),
    ).rejects.toBeInstanceOf(SessionEndedError);
    expect(manager.signedIn).toBe(false);
  });
});
