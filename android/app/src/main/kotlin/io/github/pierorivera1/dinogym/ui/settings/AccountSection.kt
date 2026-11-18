package io.github.pierorivera1.dinogym.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.PixelIcon
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * Settings' ACCOUNT section (mock `Account.dc.html`, DESIGN.md "Account (task 20)").
 * Signed out: the cloud tile, what an account is for, SIGN IN WITH GOOGLE and the privacy
 * note. Signed in: the initial tile (no photo, so nothing loads from the network), name,
 * email and SIGN OUT. The sync status row and SYNC NOW come with sync in task 21.
 * Nothing shows while the stored session is being read.
 */
@Composable
internal fun AccountSection(state: AccountUiState, actions: SettingsActions) {
    if (state == AccountUiState.Loading) return
    Field(stringResource(R.string.account_label)) {
        val colors = DinoTheme.colors
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SteppedShape)
                .background(colors.surface)
                .steppedBorder(colors.outline)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is AccountUiState.SignedOut -> SignedOutCard(state, actions)
                is AccountUiState.SignedIn -> SignedInCard(state, actions)
                AccountUiState.Loading -> Unit
            }
        }
    }
}

@Composable
private fun ColumnScope.SignedOutCard(state: AccountUiState.SignedOut, actions: SettingsActions) {
    val colors = DinoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(48.dp).clip(SteppedShape).background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(R.drawable.ic_px_cloud, colors.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.account_signed_out_title),
                style = DinoTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.outline,
            )
            Text(
                stringResource(R.string.account_signed_out_body),
                style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.muted,
            )
        }
    }
    PrimaryButton(
        text = stringResource(if (state.signingIn) R.string.account_signing_in else R.string.account_sign_in),
        onClick = actions.onSignIn,
        enabled = !state.signingIn,
        modifier = Modifier.fillMaxWidth(),
    )
    state.error?.let { error ->
        Text(
            stringResource(
                when (error) {
                    AccountError.NO_GOOGLE_ACCOUNT -> R.string.account_error_no_account
                    AccountError.OFFLINE -> R.string.account_error_offline
                    AccountError.FAILED -> R.string.account_error_failed
                },
            ),
            style = DinoTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            color = colors.danger,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Note(stringResource(R.string.account_privacy_note))
}

@Composable
private fun ColumnScope.SignedInCard(state: AccountUiState.SignedIn, actions: SettingsActions) {
    val colors = DinoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Decoration: the name next to it says the same thing.
        Box(
            Modifier
                .size(48.dp)
                .clip(SteppedShape)
                .background(colors.selBg)
                .steppedBorder(colors.selBorder)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(initialOf(state.displayName), style = DinoTheme.type.button.copy(fontSize = 24.sp), color = colors.selText)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                state.displayName,
                style = DinoTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.email,
                style = DinoTheme.type.body.copy(fontSize = 14.sp),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    SecondaryButton(stringResource(R.string.account_sign_out), actions.onAskSignOut, Modifier.fillMaxWidth())
    Note(stringResource(R.string.account_signed_in_note))
}

@Composable
private fun Note(text: String) {
    Text(text, style = DinoTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = DinoTheme.colors.muted)
}

/** The first character of [name] (a whole code point, so an emoji or accent is not cut), upper case. */
internal fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}
