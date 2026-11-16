package io.github.pierorivera1.dinogym.ui.programs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Typing is saved this long after the last key press, as in the exercise editor (decision 44). */
private const val TYPING_SAVE_DELAY_MS = 500L

/**
 * The name field of the program and routine editors. [draft] is the text as typed (null
 * until the first key press, so the screen shows the stored name). It is written
 * [TYPING_SAVE_DELAY_MS] after the last key, or at once on [flush] (the screen going to the
 * background or closing). A blank name is never written: the old name stays (decision 44).
 *
 * The delay runs in [uiScope] (cancelled with the screen); the write runs in [appScope], so
 * the last one still finishes after the ViewModel is cleared. Writes go one at a time.
 */
internal class NameSaver(
    private val uiScope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val save: suspend (String) -> Unit,
) {
    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft

    private val writeLock = Mutex()
    private var pending: Job? = null
    private var lastWritten: String? = null

    fun change(text: String) {
        _draft.value = text
        pending?.cancel()
        pending = uiScope.launch {
            delay(TYPING_SAVE_DELAY_MS)
            flush()
        }
    }

    fun flush() {
        pending?.cancel()
        pending = null
        val name = _draft.value?.trim() ?: return
        if (name.isEmpty() || name == lastWritten) return
        lastWritten = name
        appScope.launch { writeLock.withLock { save(name) } }
    }
}
