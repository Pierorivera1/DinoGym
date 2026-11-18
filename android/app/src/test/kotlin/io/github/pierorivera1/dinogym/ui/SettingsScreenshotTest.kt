package io.github.pierorivera1.dinogym.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.pierorivera1.dinogym.ui.settings.AccountError
import io.github.pierorivera1.dinogym.ui.settings.AccountUiState
import io.github.pierorivera1.dinogym.ui.settings.SyncRowState
import io.github.pierorivera1.dinogym.ui.settings.FontLicences
import io.github.pierorivera1.dinogym.ui.settings.LicencesScreen
import io.github.pierorivera1.dinogym.ui.settings.SettingsActions
import io.github.pierorivera1.dinogym.ui.settings.SettingsScreen
import io.github.pierorivera1.dinogym.ui.settings.SettingsUiState
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders Settings (with the ACCOUNT states of `Account.dc.html`) and the open-source licences in Night/English and Day/Spanish at the
 * mock's size (390×844dp), to compare with `Settings.dc.html` by eye.
 * Output: app/build/outputs/roborazzi/settings_*.png. Nothing is compared automatically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one opens the database, whose native SQLite library is not available on the JVM.
@Config(sdk = [36], qualifiers = "en-w390dp-h844dp-420dpi", application = android.app.Application::class)
class SettingsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsNightEnglish() = capture("settings_en_night", ThemeMode.NIGHT) {
        SettingsScreen(SettingsUiState(ThemeMode.SYSTEM, "2.0.0", 1), SettingsActions.None)
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun settingsDaySpanish() = capture("settings_es_day", ThemeMode.DAY) {
        SettingsScreen(SettingsUiState(ThemeMode.DAY, "2.0.0", 1), SettingsActions.None)
    }

    @Test
    fun accountSigningInNightEnglish() = capture("settings_account_signing_in_en_night", ThemeMode.NIGHT) {
        SettingsScreen(
            SettingsUiState(ThemeMode.SYSTEM, "2.0.0", 1, AccountUiState.SignedOut(signingIn = true)),
            SettingsActions.None,
        )
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun accountErrorDaySpanish() = capture("settings_account_error_es_day", ThemeMode.DAY) {
        SettingsScreen(
            SettingsUiState(ThemeMode.DAY, "2.0.0", 1, AccountUiState.SignedOut(error = AccountError.NO_GOOGLE_ACCOUNT)),
            SettingsActions.None,
        )
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun accountSignedInDaySpanish() = capture("settings_account_signed_in_es_day", ThemeMode.DAY) {
        SettingsScreen(
            SettingsUiState(
                ThemeMode.DAY,
                "2.0.0",
                1,
                AccountUiState.SignedIn("Piero", "piero@example.com", sync = SyncRowState.Synced(minutesAgo = 2)),
            ),
            SettingsActions.None,
        )
    }

    /** Mock 3: offline with changes waiting, SYNC NOW disabled. */
    @Test
    fun accountOfflineNightEnglish() = capture("settings_account_offline_en_night", ThemeMode.NIGHT) {
        SettingsScreen(
            SettingsUiState(
                ThemeMode.SYSTEM,
                "2.0.0",
                1,
                AccountUiState.SignedIn(
                    "Piero",
                    "piero@example.com",
                    sync = SyncRowState.Offline(pending = 3),
                    canSyncNow = false,
                    pendingChanges = 3,
                ),
            ),
            SettingsActions.None,
        )
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun accountSyncFailedDaySpanish() = capture("settings_account_sync_failed_es_day", ThemeMode.DAY) {
        SettingsScreen(
            SettingsUiState(
                ThemeMode.DAY,
                "2.0.0",
                1,
                AccountUiState.SignedIn("Piero", "piero@example.com", sync = SyncRowState.Failed(pending = 1), pendingChanges = 1),
            ),
            SettingsActions.None,
        )
    }

    @Test
    fun accountSignOutDialogNightEnglish() = capture("settings_account_sign_out_en_night", ThemeMode.NIGHT) {
        SettingsScreen(
            SettingsUiState(
                ThemeMode.SYSTEM,
                "2.0.0",
                1,
                AccountUiState.SignedIn(
                    "Piero",
                    "piero@example.com",
                    confirmingSignOut = true,
                    sync = SyncRowState.Offline(pending = 3),
                    canSyncNow = false,
                    pendingChanges = 3,
                ),
            ),
            SettingsActions.None,
        )
    }

    @Test
    fun licencesNightEnglish() = capture("settings_licences_en_night", ThemeMode.NIGHT) {
        LicencesScreen(openAsset = null, openText = null, onBack = {}, onToggle = {}, onOpenUrl = {})
    }

    @Test
    @Config(qualifiers = SPANISH)
    fun licencesOpenDaySpanish() = capture("settings_licences_open_es_day", ThemeMode.DAY) {
        // Inter's licence open, with the text read from the real asset.
        val asset = FontLicences.first().asset!!
        val context = LocalContext.current
        val text = remember { context.assets.open(asset).bufferedReader().use { it.readText() } }
        LicencesScreen(openAsset = asset, openText = text, onBack = {}, onToggle = {}, onOpenUrl = {})
    }

    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.setContent { DinoTheme(mode) { content() } }
        // Lets the text box finish typing.
        compose.mainClock.advanceTimeBy(5_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private companion object {
        const val SPANISH = "es-w390dp-h844dp-420dpi"
    }
}
