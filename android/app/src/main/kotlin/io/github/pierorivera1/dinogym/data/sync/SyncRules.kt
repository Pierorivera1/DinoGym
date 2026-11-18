package io.github.pierorivera1.dinogym.data.sync

import io.github.pierorivera1.dinogym.data.local.RowVersion

/**
 * The phone's half of decision 51, the same rule the server applies: whole rows, the newest
 * `updatedAt` wins, and on a tie a deletion wins. A local row that is newer than the pulled
 * one is kept whether or not it was pushed yet, so an unpushed edit is never overwritten.
 * Applying the same pulled row twice changes nothing, because the second time it is a tie.
 */
object MergeRules {

    /** True when the pulled [remote] row should replace [local] (null: the phone does not have it). */
    fun remoteWins(local: RowVersion?, remote: RowVersion): Boolean = when {
        local == null -> true
        remote.updatedAt != local.updatedAt -> remote.updatedAt > local.updatedAt
        else -> remote.deletedAt != null && local.deletedAt == null
    }
}

/**
 * Splits a push into requests the server takes (decisions 62 and 87): at most [MAX_ROWS] rows
 * and about [MAX_BYTES] of JSON each, under API Gateway's and the handler's 1 MiB body limit
 * with room for the request's own brackets and keys. Set notes have no length limit, so the
 * size matters as much as the count. A single row bigger than the limit still goes alone;
 * if the server refuses it (413), it stays pending and sync reports an error.
 */
object PushBatcher {
    const val MAX_ROWS = 500
    const val MAX_BYTES = 900_000

    fun <T> split(items: List<T>, maxRows: Int = MAX_ROWS, maxBytes: Int = MAX_BYTES, size: (T) -> Int): List<List<T>> {
        val batches = mutableListOf<List<T>>()
        var current = mutableListOf<T>()
        var bytes = 0
        for (item in items) {
            val itemBytes = size(item) + 1 // the comma between rows
            if (current.isNotEmpty() && (current.size == maxRows || bytes + itemBytes > maxBytes)) {
                batches += current
                current = mutableListOf()
                bytes = 0
            }
            current += item
            bytes += itemBytes
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }
}

/** What applying a page needs from the database, so the rules run on the JVM with a fake. */
interface ApplyTarget {
    suspend fun version(ref: RowRef): RowVersion?

    /** Writes [row] (insert or update in place) and drops its pending entry: the pulled row won. */
    suspend fun write(row: SyncRow)

    /** Rows that waited for a parent on an earlier page. */
    suspend fun inbox(): List<SyncRow>

    suspend fun putInbox(row: SyncRow)

    suspend fun removeInbox(ref: RowRef)
}

/**
 * Applies one pulled page (decision 89). Runs inside one Room transaction, with the cursor
 * stored in the same transaction, so a page is either applied whole or not at all.
 *
 * - The page and the rows waiting in the inbox are merged, the newer copy of a row winning.
 * - They are applied parents first ([TABLE_ORDER]), so a session and its sets on the same page
 *   are written in the right order.
 * - A row that loses to the phone's copy ([MergeRules]) is dropped.
 * - A row whose parent the phone does not have yet goes to the inbox instead of failing the
 *   foreign key; it is written once the parent arrives on a later page or a later sync.
 */
object PageApplier {

    data class Counts(val written: Int, val skipped: Int, val waiting: Int)

    suspend fun apply(page: List<SyncRow>, target: ApplyTarget): Counts {
        val newest = LinkedHashMap<RowRef, SyncRow>()
        (target.inbox() + page).forEach { row ->
            val seen = newest[row.ref]
            if (seen == null || MergeRules.remoteWins(seen.version, row.version)) newest[row.ref] = row
        }
        var written = 0
        var skipped = 0
        var waiting = 0
        for (row in newest.values.toList().inTableOrder { it.ref }) {
            when {
                !MergeRules.remoteWins(target.version(row.ref), row.version) -> {
                    target.removeInbox(row.ref)
                    skipped++
                }
                row.parents.any { target.version(it) == null } -> {
                    target.putInbox(row)
                    waiting++
                }
                else -> {
                    target.write(row)
                    target.removeInbox(row.ref)
                    written++
                }
            }
        }
        return Counts(written, skipped, waiting)
    }
}
