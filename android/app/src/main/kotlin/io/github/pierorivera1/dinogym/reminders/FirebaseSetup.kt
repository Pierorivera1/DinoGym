package io.github.pierorivera1.dinogym.reminders

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pierorivera1.dinogym.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Starts Firebase from BuildConfig values instead of the Google Services Gradle plugin
 * (decision 109): the app needs only messaging and Crashlytics, and the API key stays out of
 * git. Crashlytics needs no code: it starts with the default app (decision 120). A build without
 * the key (CI's lint and unit-test builds) skips Firebase; reminders and crash reports are off.
 */
@Singleton
class FirebaseSetup @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True once Firebase is running in this process. */
    val isReady: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    fun start() {
        if (BuildConfig.FIREBASE_API_KEY.isBlank() || isReady) return
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
    }
}

/** [PushTokens] from Firebase Messaging. */
class FirebasePushTokens @Inject constructor(
    private val firebase: FirebaseSetup,
) : PushTokens {
    override suspend fun current(): String? {
        if (!firebase.isReady) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // No Play services, or Google not reachable: try again at the next app start.
            null
        }
    }
}

/** Waits for a Play services [Task] without the extra coroutines-play-services library. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}
