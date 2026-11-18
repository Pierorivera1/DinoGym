package io.github.pierorivera1.dinogym.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme

/**
 * "Are you sure?" for actions that cannot be undone from the screen, such as deleting a whole
 * program, routine or session (decision 41). A [WindowSheet] with [title], a [message], and
 * two buttons: CANCEL and the [confirmLabel] in danger style (DELETE). The scrim, the close
 * box and CANCEL all call [onDismiss]. With [destructive] false the confirm button is a plain
 * primary button with no trash icon, for a choice that loses nothing (SIGN OUT, task 20).
 *
 * Like the workout's sheets it is drawn inside the screen: put it last in a full-screen Box
 * while it is open, and close it on system Back with a BackHandler.
 */
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(R.string.action_cancel),
    destructive: Boolean = true,
) {
    WindowSheet(title, onDismiss, stringResource(R.string.cd_close), modifier) {
        Text(message, style = DinoTheme.type.body.copy(fontSize = 15.sp), color = DinoTheme.colors.outline)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(dismissLabel, onDismiss, Modifier.weight(1f))
            if (destructive) {
                DangerButton(confirmLabel, onConfirm, Modifier.weight(1f), icon = R.drawable.ic_px_trash)
            } else {
                PrimaryButton(confirmLabel, onConfirm, Modifier.weight(1f))
            }
        }
    }
}
