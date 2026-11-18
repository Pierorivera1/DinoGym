package io.github.pierorivera1.dinogym.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.pierorivera1.dinogym.di.AccountStore
import io.github.pierorivera1.dinogym.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Where the signed-in session is kept. An interface so SessionManager is tested with a fake. */
interface SessionStore {
    /** The stored session, or null when signed out. */
    val session: Flow<StoredSession?>

    suspend fun read(): StoredSession? = session.first()

    suspend fun save(session: StoredSession)

    /** Signs out locally: the session, its tokens, the name and the email are deleted. */
    suspend fun clear()
}

/**
 * The session in its own DataStore file, `account`, apart from the theme setting. The whole
 * [StoredSession] is one value: JSON, encrypted by [TokenCipher], then Base64. Nothing in the
 * file is readable without the Keystore key (decision 81). The app has backups off
 * (`allowBackup="false"`), so the file never reaches a phone without that key.
 *
 * A value that cannot be decrypted or parsed (a lost key, a damaged file, an old format)
 * reads as signed out; the next sign-in overwrites it.
 */
@Singleton
class DataStoreSessionStore @Inject constructor(
    @AccountStore private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SessionStore {

    override val session: Flow<StoredSession?> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SESSION_KEY]?.let(::decode) }
        .flowOn(io)
        .distinctUntilChanged()

    override suspend fun save(session: StoredSession) {
        val sealed = withContext(io) { encode(session) }
        store.edit { it[SESSION_KEY] = sealed }
    }

    override suspend fun clear() {
        store.edit { it.remove(SESSION_KEY) }
    }

    private fun encode(session: StoredSession): String =
        Base64.getEncoder().encodeToString(cipher.encrypt(JSON.encodeToString(session).encodeToByteArray()))

    @Suppress("TooGenericExceptionCaught") // Keystore, Base64 and JSON each throw their own kinds.
    private fun decode(stored: String): StoredSession? = try {
        JSON.decodeFromString<StoredSession>(cipher.decrypt(Base64.getDecoder().decode(stored)).decodeToString())
    } catch (_: Exception) {
        null
    }

    private companion object {
        val SESSION_KEY = stringPreferencesKey("session")
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
