package io.github.pierorivera1.dinogym.reminders

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the server's data messages and FCM's new tokens (decisions 104 and 106). A data
 * message holds only `kind`; the words come from [ReminderNotifier]. A message that arrives
 * signed out (a DELETE that never reached the server) is dropped.
 */
@AndroidEntryPoint
class ReminderMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var registry: DeviceRegistry

    @Inject
    lateinit var sessions: SessionManager

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onMessageReceived(message: RemoteMessage) {
        val kind = ReminderKind.fromWire(message.data["kind"]) ?: return
        // A high-priority message gives the app about ten seconds; reading Room takes far less.
        // The application scope outlives this call, which returns at once as Android expects.
        scope.launch {
            if (sessions.userId.first() == null) return@launch
            notifier.show(kind)
        }
    }

    override fun onNewToken(token: String) {
        scope.launch { registry.register(newToken = token) }
    }
}
