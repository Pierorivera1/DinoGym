package io.github.pierorivera1.dinogym.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pierorivera1.dinogym.BuildConfig
import io.github.pierorivera1.dinogym.R

/** The project's public repository, opened by the Source code row. */
const val SOURCE_URL = "https://github.com/Pierorivera1/DinoGym"

/**
 * Settings as a navigation entry: connects [SettingsViewModel] to the stateless
 * [SettingsScreen] and opens links in the browser.
 */
@Composable
fun SettingsEntry(
    onBack: () -> Unit,
    onOpenLicences: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreen(
        state = SettingsUiState(themeMode, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        actions = SettingsActions(
            onBack = onBack,
            onThemeChange = viewModel::setThemeMode,
            onOpenSource = { context.openLink(SOURCE_URL) },
            onOpenLicences = onOpenLicences,
        ),
    )
}

/**
 * Opens [url] in the browser. A phone with no browser (rare, but possible on work profiles
 * or stripped-down devices) gets a short message instead of a crash.
 */
internal fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.settings_no_browser, Toast.LENGTH_SHORT).show()
    }
}
