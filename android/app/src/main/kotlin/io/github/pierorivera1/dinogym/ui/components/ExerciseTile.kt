package io.github.pierorivera1.dinogym.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pierorivera1.dinogym.R
import io.github.pierorivera1.dinogym.domain.model.ExerciseIcon
import io.github.pierorivera1.dinogym.ui.theme.DinoTheme
import io.github.pierorivera1.dinogym.ui.theme.SteppedShape

/**
 * The pixel drawable for an exercise's icon key (decision 30). GENERIC ("Other" in the
 * editor) uses the dumbbell, the same icon as the Exercises tab.
 */
@DrawableRes
fun ExerciseIcon.drawable(): Int = when (this) {
    ExerciseIcon.PULL_UP -> R.drawable.ic_px_pull
    ExerciseIcon.ROW -> R.drawable.ic_px_row
    ExerciseIcon.CURL -> R.drawable.ic_px_curl
    ExerciseIcon.DIP -> R.drawable.ic_px_dip
    ExerciseIcon.PUSH_UP -> R.drawable.ic_px_pushup
    ExerciseIcon.RAISE -> R.drawable.ic_px_raise
    ExerciseIcon.SQUAT -> R.drawable.ic_px_squat
    ExerciseIcon.LEVER -> R.drawable.ic_px_lever
    ExerciseIcon.JUMP_ROPE -> R.drawable.ic_px_jump_rope
    ExerciseIcon.GENERIC -> R.drawable.ic_px_exercises
}

/**
 * An exercise's icon on a stepped `surface2` tile, as on the active exercise screen
 * (64dp tile, icon at 3x). The library uses a 56dp tile with a 48dp icon ([iconSize]).
 * Decorative: the exercise name is always next to it.
 */
@Composable
fun ExerciseTile(icon: ExerciseIcon, modifier: Modifier = Modifier, size: Dp = 64.dp, iconSize: Dp = size * 3 / 4) {
    val colors = DinoTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(SteppedShape)
            .background(colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(icon.drawable(), colors.outline, size = iconSize)
    }
}
