import { describe, expect, it, vi } from "vitest";
import { ApiClient } from "../api/client";
import { SessionManager } from "../api/session";
import type { PullResponse } from "../api/types";
import { emptyDataset } from "./dataset";
import { pullInto } from "./load";

const routine = (id: string, name: string, deletedAt: number | null = null) => ({ id, name, updatedAt: 1, deletedAt });

async function signedIn(pages: PullResponse[]) {
  const api = new ApiClient();
  vi.spyOn(api, "signInWithGoogle").mockResolvedValue({ userId: "u", accessToken: "a", accessTokenExpiresAt: Date.now() + 3_600_000, refreshToken: "r" });
  const pull = vi.spyOn(api, "pull");
  for (const p of pages) pull.mockResolvedValueOnce(p);
  const session = new SessionManager(api, () => Date.now(), () => true);
  await session.signIn("google");
  return { api, session, pull };
}

describe("pullInto", () => {
  it("follows the cursor until hasMore is false and keeps the newest copy of each row", async () => {
    const { api, session, pull } = await signedIn([
      { changes: { routines: [routine("a", "PULL"), routine("b", "PUSH")] }, cursor: 2, hasMore: true },
      { changes: { routines: [routine("a", "PULL DAY"), routine("b", "PUSH", 5)] }, cursor: 4, hasMore: false },
    ]);
    const ds = emptyDataset();
    expect(await pullInto(ds, api, session, 0)).toBe(4);
    expect(pull.mock.calls.map((c) => c[1])).toEqual([0, 2]);
    expect([...ds.routines.values()].map((r) => r.name)).toEqual(["PULL DAY"]);
  });

  it("stops when a page does not move the cursor", async () => {
    const { api, session, pull } = await signedIn([{ changes: {}, cursor: 7, hasMore: true }]);
    expect(await pullInto(emptyDataset(), api, session, 7)).toBe(7);
    expect(pull).toHaveBeenCalledTimes(1);
  });
});
