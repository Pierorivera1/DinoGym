package io.github.pierorivera1.dinogym.ui.workout

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock

/**
 * The current time, emitted at once and then on every whole second of [clock]. The workout
 * screens combine it into their state for the elapsed time and the rest countdown. It runs
 * only while a screen collects it: the ViewModels share their state with `WhileSubscribed`
 * and the screens collect with `collectAsStateWithLifecycle`, so it stops with the screen off.
 */
fun secondTicker(clock: Clock): Flow<Long> = flow {
    while (true) {
        val now = clock.millis()
        emit(now)
        delay(1_000 - now % 1_000)
    }
}
