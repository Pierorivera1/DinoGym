package io.github.pierorivera1.dinogym.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.ConfirmSheet
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.SegmentedControl
import io.github.pierorivera1.dinogym.ui.components.TextBox
import io.github.pierorivera1.dinogym.ui.components.TopBar
import io.github.pierorivera1.dinogym.ui.components.dottedBottomLine
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.ThemeMode
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/** What Settings shows. [themeMode] is null for the moment before the setting is read. */
data class SettingsUiState(
    val themeMode: ThemeMode?,
    val versionName: String,
    val versionCode: Int,
    val account: AccountUiState = AccountUiState.SignedOut(),
)

/** Settings' taps. The theme is stored as soon as it is picked. */
data class SettingsActions(
    val onBack: () -> Unit,
    val onThemeChange: (ThemeMode) -> Unit,
    val onOpenSource: () -> Unit,
    val onOpenLicences: () -> Unit,
    val onSignIn: () -> Unit = {},
    val onAskSignOut: () -> Unit = {},
    val onCancelSignOut: () -> Unit = {},
    val onConfirmSignOut: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
) {
    companion object {
        /** No-op actions, for screenshot tests. */
        val None = SettingsActions({}, {}, {}, {})
    }
}

/** The THEME options in the order of the mock. */
private val ThemeChoices = listOf(
    ThemeMode.SYSTEM to R.string.settings_theme_system,
    ThemeMode.NIGHT to R.string.settings_theme_night,
    ThemeMode.DAY to R.string.settings_theme_day,
)

/**
 * Settings (mock `Settings.dc.html`): top bar with back; ACCOUNT (task 20, `Account.dc.html`)
 * with its sign-out dialog drawn over the screen; THEME (System / Night / Day);
 * LANGUAGE, read-only because the app follows the phone; ABOUT with the version, the source
 * code link, the open-source licences and the app's MIT licence; the mascot's text box at
 * the bottom. A pushed screen: no bottom bar. Stateless.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = DinoTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val viewportHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // At least a screen tall, so the text box sits at the bottom as in the mock.
                .heightIn(min = viewportHeight)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                title = stringResource(R.string.settings_title),
                onBack = actions.onBack,
                backDescription = stringResource(R.string.settings_back_cd),
            )
            AccountSection(state.account, actions)
            Field(stringResource(R.string.settings_theme_label)) {
                SegmentedControl(
                    options = ThemeChoices.map { stringResource(it.second) },
                    selectedIndex = ThemeChoices.indexOfFirst { it.first == state.themeMode },
                    onSelect = { actions.onThemeChange(ThemeChoices[it].first) },
                )
                HelpText(stringResource(R.string.settings_theme_help))
            }
            Field(stringResource(R.string.settings_language_label)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(SteppedShape)
                        .background(colors.surface)
                        .steppedBorder(colors.surface2)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_language_current),
                        style = DinoTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.outline,
                    )
                    HelpText(stringResource(R.string.settings_language_help))
                }
            }
            Field(stringResource(R.string.settings_about_label)) {
                AboutList(state, actions)
            }
            Spacer(Modifier.weight(1f))
            TextBox(stringResource(R.string.settings_bubble), showMascot = true, modifier = Modifier.fillMaxWidth())
        }
        val signedIn = state.account as? AccountUiState.SignedIn
        if (signedIn?.confirmingSignOut == true) {
            val pending = signedIn.pendingChanges
            ConfirmSheet(
                title = stringResource(R.string.account_sign_out_title),
                // With unsynced changes, say they wait for the next sign-in (mock `Account.dc.html`).
                message = if (pending > 0) {
                    pluralStringResource(R.plurals.account_sign_out_body_pending, pending, pending)
                } else {
                    stringResource(R.string.account_sign_out_body)
                },
                confirmLabel = stringResource(R.string.account_sign_out),
                onConfirm = actions.onConfirmSignOut,
                onDismiss = actions.onCancelSignOut,
                destructive = false,
            )
        }
    }
}

/** A labelled block: the label (a heading for screen readers) and its content, 6dp apart. */
@Composable
internal fun Field(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = DinoTheme.type.label.copy(fontSize = 13.sp),
            color = DinoTheme.colors.muted,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = DinoTheme.colors.muted)
}

@Composable
private fun AboutList(state: SettingsUiState, actions: SettingsActions) {
    val colors = DinoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SteppedShape)
            .background(colors.surface)
            .steppedBorder(colors.outline),
    ) {
        ListRow(stringResource(R.string.settings_version), divider = true) {
            ValueText(stringResource(R.string.settings_version_value, state.versionName, state.versionCode))
        }
        ListRow(stringResource(R.string.settings_source), divider = true, onClick = actions.onOpenSource) {
            Text(stringResource(R.string.settings_source_host), style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.muted)
        }
        ListRow(stringResource(R.string.settings_licences), divider = true, onClick = actions.onOpenLicences)
        ListRow(stringResource(R.string.settings_licence), divider = false) {
            ValueText(stringResource(R.string.settings_licence_value))
        }
    }
}

@Composable
private fun ValueText(text: String) {
    Text(text, style = DinoTheme.type.button.copy(fontSize = 16.sp, lineHeight = 20.sp), color = DinoTheme.colors.muted)
}

/**
 * A 52dp row of a list box: [title] on the left, [trailing] on the right. With [onClick] the
 * whole row is a button and ends in a chevron. [divider] draws the dotted line under it.
 */
@Composable
internal fun ListRow(
    title: String,
    divider: Boolean,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = DinoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (divider) Modifier.dottedBottomLine(colors.surface2) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = if (onClick != null) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = DinoTheme.type.body.copy(fontSize = 15.sp),
            color = colors.outline,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        trailing?.invoke()
        if (onClick != null) PixelIcon(R.drawable.ic_px_chevron, colors.muted, size = 24.dp)
    }
}
