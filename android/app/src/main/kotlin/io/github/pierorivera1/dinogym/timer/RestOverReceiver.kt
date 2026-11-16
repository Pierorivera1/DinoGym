package io.github.pierorivera1.dinogym.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.pierorivera1.dinogym.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receives the end-of-rest alarm from [RestAlarm]. It may run in a fresh process (the app
 * was killed during the rest); Android creates the Application first, then calls this.
 *
 * Dependencies come from a Hilt entry point instead of `@AndroidEntryPoint`, so it is plain
 * code with no generated base class to reason about.
 */
class RestOverReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun restTimer(): RestTimer

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val nextLabel = intent.getStringExtra(EXTRA_NEXT_LABEL).orEmpty()
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        // goAsync keeps the process alive until the saved state has been cleared.
        val pending = goAsync()
        deps.scope().launch {
            try {
                deps.restTimer().onRestOver(nextLabel)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_NEXT_LABEL = "next_label"
    }
}
