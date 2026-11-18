package io.github.pierorivera1.dinogym.data.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.pierorivera1.dinogym.BuildConfig

/** What Google's sign-in sheet gave back. */
sealed interface GoogleCredentialResult {
    /** [email] is the credential's id; [displayName] falls back to it when Google has no name. */
    data class Success(val idToken: String, val displayName: String, val email: String) : GoogleCredentialResult

    /** The person closed the sheet. Not an error. */
    data object Cancelled : GoogleCredentialResult

    /** No Google account on the phone, or none that can be used. */
    data object NoAccount : GoogleCredentialResult

    data object Failed : GoogleCredentialResult
}

/**
 * Asks Android's Credential Manager for a Google ID token (decision 81). Uses
 * [GetSignInWithGoogleOption], the flow behind an explicit "Sign in with Google" button, with
 * the Web client ID as the server client ID, so the token's audience is what the backend
 * checks (decision 80).
 *
 * [activityContext] must be an Activity: the sheet is shown over it.
 */
suspend fun requestGoogleIdToken(activityContext: Context): GoogleCredentialResult {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleCredentialResult.Success(
                idToken = google.idToken,
                displayName = google.displayName?.takeIf { it.isNotBlank() } ?: google.id,
                email = google.id,
            )
        } else {
            GoogleCredentialResult.Failed
        }
    } catch (_: GetCredentialCancellationException) {
        GoogleCredentialResult.Cancelled
    } catch (_: NoCredentialException) {
        GoogleCredentialResult.NoAccount
    } catch (_: GetCredentialException) {
        GoogleCredentialResult.Failed
    } catch (_: GoogleIdTokenParsingException) {
        GoogleCredentialResult.Failed
    }
}
