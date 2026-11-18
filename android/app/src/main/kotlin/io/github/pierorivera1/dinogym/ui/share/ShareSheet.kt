package io.github.pierorivera1.dinogym.ui.share

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.components.PrimaryButton
import io.github.pierorivera1.dinogym.ui.components.SecondaryButton
import io.github.pierorivera1.dinogym.ui.components.WindowSheet
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape
import io.github.pierorivera1.dinogym.ui.theme.steppedBorder

/**
 * The SHARE ROUTINE / SHARE PROGRAM button at the end of an editor (decision 103). The
 * editor's entry connects [onClick] to [ShareViewModel.share] and draws [ShareSheetHost].
 */
@Composable
fun ShareButton(isProgram: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SecondaryButton(
        text = stringResource(if (isProgram) R.string.share_program else R.string.share_routine),
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        icon = R.drawable.ic_px_share,
    )
}

/**
 * The share sheet over an editor, drawn last in the editor's Box like its other sheets.
 * [name] goes in the message sent with the link; [onOpenSettings] leads to sign-in.
 */
@Composable
fun ShareSheetHost(
    name: String,
    onOpenSettings: () -> Unit,
    viewModel: ShareViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val context = LocalContext.current
    BackHandler(onBack = viewModel::close)
    LaunchedEffect(current.sendNow) {
        if (current.sendNow && current.url != null) {
            sendLink(context, name, current.url)
            viewModel.sent()
        }
    }
    ShareSheet(
        state = current,
        onClose = viewModel::close,
        onRetry = viewModel::retry,
        onSend = { current.url?.let { sendLink(context, name, it) } },
        onOpenSettings = {
            viewModel.close()
            onOpenSettings()
        },
    )
}

/** Opens Android's share sheet with the link as plain text. */
private fun sendLink(context: Context, name: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message, name, url))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
}

/** Stateless, for screenshots: the sheet for [state]. */
@Composable
fun ShareSheet(
    state: ShareUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = DinoTheme.colors
    WindowSheet(stringResource(R.string.share_sheet_title), onClose, stringResource(R.string.cd_close)) {
        val program = state.target is ShareTarget.Program
        val message = when (state.step) {
            ShareUiState.Step.WORKING -> stringResource(R.string.share_working)
            ShareUiState.Step.DONE -> stringResource(if (program) R.string.share_done_program else R.string.share_done)
            ShareUiState.Step.SIGNED_OUT -> stringResource(R.string.share_signed_out)
            ShareUiState.Step.OFFLINE -> stringResource(R.string.share_offline)
            ShareUiState.Step.TOO_MANY -> stringResource(R.string.share_too_many)
            ShareUiState.Step.TOO_BIG -> stringResource(R.string.share_too_big)
            ShareUiState.Step.EMPTY -> stringResource(R.string.share_empty)
            ShareUiState.Step.FAILED -> stringResource(R.string.share_failed)
        }
        val error = state.step in setOf(ShareUiState.Step.TOO_MANY, ShareUiState.Step.TOO_BIG, ShareUiState.Step.FAILED)
        Text(
            message,
            style = DinoTheme.type.body.copy(fontSize = 15.sp),
            color = if (error) colors.danger else colors.outline,
        )
        if (state.step == ShareUiState.Step.DONE && state.url != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg, SteppedShape)
                    .steppedBorder(colors.outline)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                SelectionContainer {
                    Text(state.url, style = DinoTheme.type.body.copy(fontSize = 14.sp), color = colors.outline)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.step) {
                ShareUiState.Step.WORKING -> Unit
                ShareUiState.Step.DONE -> {
                    SecondaryButton(stringResource(R.string.action_close), onClose, Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.share_send), onSend, Modifier.weight(1f))
                }
                ShareUiState.Step.SIGNED_OUT -> {
                    SecondaryButton(stringResource(R.string.action_cancel), onClose, Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.share_open_settings), onOpenSettings, Modifier.weight(1f))
                }
                ShareUiState.Step.OFFLINE, ShareUiState.Step.FAILED -> {
                    SecondaryButton(stringResource(R.string.action_close), onClose, Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.action_retry), onRetry, Modifier.weight(1f))
                }
                ShareUiState.Step.TOO_MANY, ShareUiState.Step.TOO_BIG, ShareUiState.Step.EMPTY ->
                    SecondaryButton(stringResource(R.string.action_close), onClose, Modifier.weight(1f))
            }
        }
    }
}
