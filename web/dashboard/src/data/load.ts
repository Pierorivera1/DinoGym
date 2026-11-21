import type { ApiClient } from "../api/client";
import type { SessionManager } from "../api/session";
import { applyChanges, type Dataset } from "./dataset";

/**
 * Pulls every page after [since] into [dataset] and returns the new cursor.
 * The first load starts at 0; REFRESH starts at the cursor it returned.
 */
export async function pullInto(dataset: Dataset, api: ApiClient, session: SessionManager, since: number): Promise<number> {
  let cursor = since;
  for (;;) {
    const page = await session.authorized((token) => api.pull(token, cursor));
    applyChanges(dataset, page.changes);
    // The cursor never goes back (the contract); a page that does not move it ends the loop.
    const moved = page.cursor > cursor;
    cursor = page.cursor;
    if (!page.hasMore || !moved) return cursor;
  }
}
