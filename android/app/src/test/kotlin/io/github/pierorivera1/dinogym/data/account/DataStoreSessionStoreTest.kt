package io.github.pierorivera1.dinogym.data.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The storage half of the session: JSON, sealed by the cipher, in a DataStore file. The real
 * cipher needs the Android Keystore, so a reversible fake stands in for it.
 */
class DataStoreSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Not encryption: flips every byte and adds a marker, so a foreign value fails to "decrypt". */
    private class FakeCipher : TokenCipher {
        var broken = false

        override fun encrypt(plain: ByteArray) = MARKER + plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun decrypt(sealed: ByteArray): ByteArray {
            check(!broken && sealed.take(MARKER.size) == MARKER.toList()) { "not ours" }
            return sealed.drop(MARKER.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }

        private companion object {
            val MARKER = byteArrayOf(1, 2, 3)
        }
    }

    private fun newStore(file: File, cipher: TokenCipher, scope: CoroutineScope) =
        DataStoreSessionStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            cipher,
            Dispatchers.Unconfined,
        )

    @Test
    fun saveReadClear_roundTrip_andNothingReadableOnDisk() = runTest {
        val file = File(folder.root, "account.preferences_pb")
        val store = newStore(file, FakeCipher(), backgroundScope)
        val session = storedSession(access = "secret-access", refresh = "secret-refresh", pending = true)

        assertNull(store.read())
        store.save(session)
        assertEquals(session, store.read())

        val onDisk = file.readBytes().decodeToString()
        for (secret in listOf("secret-access", "secret-refresh", "piero@example.com", "Piero")) {
            assertFalse("$secret is not stored in the clear", secret in onDisk)
        }

        store.clear()
        assertNull(store.read())
    }

    @Test
    fun aValueThatCannotBeDecrypted_readsAsSignedOut() = runTest {
        val cipher = FakeCipher()
        val store = newStore(File(folder.root, "account.preferences_pb"), cipher, backgroundScope)
        store.save(storedSession())

        // As if the Keystore key were gone.
        cipher.broken = true

        assertNull(store.read())
    }
}
