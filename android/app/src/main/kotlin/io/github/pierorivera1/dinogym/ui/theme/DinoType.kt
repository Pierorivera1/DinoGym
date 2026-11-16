package io.github.pierorivera1.dinogym.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pierorivera1.dinogym.R

// Both fonts are bundled variable fonts (res/font). Each Font entry pins the weight axis,
// so the same file serves Regular and SemiBold. Variation settings work from API 26.

/** Pixelify Sans: short, big text (numbers, titles, buttons, nav labels). */
val PixelifySans = FontFamily(
    Font(
        R.font.pixelify_sans,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** Inter: everything people read. */
val Inter = FontFamily(
    Font(
        R.font.inter,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/**
 * The type styles from docs/DESIGN.md "Type". Sizes are the default for each role;
 * a screen that needs another size in the documented range uses `.copy(fontSize = ...)`.
 */
@Immutable
data class DinoType(
    val numberXl: TextStyle,
    val title: TextStyle,
    val button: TextStyle,
    val navLabel: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
)

/** The one set of type styles. Colours come from [DinoColors], so both themes share it. */
val DinoTypeDefault = DinoType(
    numberXl = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, lineHeight = 64.sp),
    title = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp),
    button = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
    navLabel = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 11.sp),
    heading = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.06.em),
)
