package io.github.pierorivera1.dinogym.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pierorivera1.dinogym.BuildConfig
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.data.account.requestGoogleIdToken

/** The project's public repository, opened by the Source code row. */
const val SOURCE_URL = "https://github.com/Pierorivera1/DinoGym"

/**
 * Settings as a navigation entry: connects [SettingsViewModel], [AccountViewModel] and
 * [RemindersViewModel] to the stateless [SettingsScreen], and opens links in the browser,
 * Google's sign-in sheet and Android's notification permission.
 */
@Composable
fun SettingsEntry(
    onBack: () -> Unit,
    onOpenLicences: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
    remindersViewModel: RemindersViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val account by accountViewModel.state.collectAsStateWithLifecycle()
    val reminders by remindersViewModel.state.collectAsStateWithLifecycle()
    // The Activity: Google's sign-in sheet is shown over it.
    val context = LocalContext.current
    BackHandler(
        enabled = (account as? AccountUiState.SignedIn)?.confirmingSignOut == true,
        onBack = accountViewModel::cancelSignOut,
    )
    BackHandler(
        enabled = (reminders as? RemindersUiState.SignedIn)?.editingTime == true,
        onBack = remindersViewModel::closeTime,
    )
    // Notifications may have been allowed or blocked in Android settings meanwhile.
    LifecycleResumeEffect(Unit) {
        remindersViewModel.refreshPermission()
        onPauseOrDispose {}
    }
    val activity = LocalActivity.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindersViewModel.refreshPermission()
        // Android no longer shows the dialog after the second "Don't allow": send the person
        // to the app's notification settings instead (DESIGN.md "Reminders").
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        if (!granted && !canAskAgain) context.openNotificationSettings()
    }
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.openNotificationSettings()
        }
    }
    val turnOn: (Boolean, (Boolean) -> Unit) -> Unit = { on, set ->
        set(on)
        // Turning a reminder on is when the permission makes sense to ask for.
        if (on && (reminders as? RemindersUiState.SignedIn)?.notificationsAllowed == false &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    SettingsScreen(
        state = SettingsUiState(
            themeMode,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            account,
            reminders,
            is24Hour = DateFormat.is24HourFormat(context),
        ),
        actions = SettingsActions(
            onBack = onBack,
            onThemeChange = viewModel::setThemeMode,
            onOpenSource = { context.openLink(SOURCE_URL) },
            onOpenLicences = onOpenLicences,
            onSignIn = { accountViewModel.signIn { requestGoogleIdToken(context) } },
            onAskSignOut = accountViewModel::askSignOut,
            onCancelSignOut = accountViewModel::cancelSignOut,
            onConfirmSignOut = accountViewModel::confirmSignOut,
            onSyncNow = accountViewModel::syncNow,
            onWorkoutDayChange = { turnOn(it, remindersViewModel::setWorkoutDay) },
            onWeeklyChange = { turnOn(it, remindersViewModel::setWeekly) },
            onOpenReminderTime = remindersViewModel::openTime,
            onCloseReminderTime = remindersViewModel::closeTime,
            onSaveReminderTime = remindersViewModel::saveTime,
            onAllowNotifications = askForNotifications,
        ),
    )
}

/** Android's notification settings for this app. */
private fun Context.openNotificationSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    } catch (_: ActivityNotFoundException) {
        // A stripped-down phone with no such screen: nothing better to open.
    }
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
