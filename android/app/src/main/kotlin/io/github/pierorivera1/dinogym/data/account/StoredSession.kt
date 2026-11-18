package io.github.pierorivera1.dinogym.data.account

import kotlinx.serialization.Serializable

/**
 * A signed-in session as kept on the phone (task 20, decision 81): the tokens from the
 * backend's `Session`, plus the name and email from Google for the ACCOUNT card. The backend
 * stores neither the name nor the email (decision 80).
 *
 * [refreshPending] is set just before a refresh token is sent and cleared when the answer is
 * stored. If the app finds it set, the token may already be spent on the server, so it is
 * never sent again (decision 79): the session is ended locally instead.
 */
@Serializable
data class StoredSession(
    val userId: String,
    val accessToken: String,
    val accessTokenExpiresAt: Long,
    val refreshToken: String,
    val displayName: String,
    val email: String,
    val refreshPending: Boolean = false,
)

/** Who is signed in, for the ACCOUNT card. */
data class Account(val displayName: String, val email: String)
