package io.github.pierorivera1.dinogym.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour token from docs/DESIGN.md "Tokens". Screens read these through
 * [DinoTheme.colors] instead of M3's ColorScheme, because the design has more
 * roles (select, action, bands, the selected-state trio) than M3 offers.
 */
@Immutable
data class DinoColors(
    val isNight: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val outline: Color,
    val muted: Color,
    val faint: Color,
    val primary: Color,
    val onPrimary: Color,
    val select: Color,
    val onSelect: Color,
    val action: Color,
    val danger: Color,
    val success: Color,
    val shadow: Color,
    val bandHigh: Color,
    val bandLow: Color,
    val scrim: Color,
    val bestRow: Color,
    // Selected state: a yellow outline in Night, a butter-yellow fill in Day.
    val selBg: Color,
    val selBorder: Color,
    val selText: Color,
    // Day-only decorative fills. Night has no equivalent, so they are null there.
    val pink: Color?,
    val teal: Color?,
)

/** The dark theme. */
val NightColors = DinoColors(
    isNight = true,
    bg = Color(0xFF0C0915),
    surface = Color(0xFF1C1430),
    surface2 = Color(0xFF2A1F45),
    outline = Color(0xFFF4F2F7),
    muted = Color(0xFFA39BB6),
    faint = Color(0xFF6E6584),
    primary = Color(0xFFA98BFF),
    onPrimary = Color(0xFF0C0915),
    select = Color(0xFFF5D23A),
    onSelect = Color(0xFF0C0915),
    action = Color(0xFFF0A030),
    danger = Color(0xFFFF4D4D),
    success = Color(0xFF5CD27A),
    shadow = Color(0xFF4A3A80),
    bandHigh = Color.White.copy(alpha = 0.22f),
    bandLow = Color.Black.copy(alpha = 0.45f),
    scrim = Color(0xFF0C0915).copy(alpha = 0.78f),
    bestRow = Color(0xFF5CD27A).copy(alpha = 0.14f),
    selBg = Color(0xFF1C1430),
    selBorder = Color(0xFFF5D23A),
    selText = Color(0xFFF5D23A),
    pink = null,
    teal = null,
)

/** The light theme. */
val DayColors = DinoColors(
    isNight = false,
    bg = Color(0xFFFBF3E8),
    surface = Color(0xFFFFFDF8),
    surface2 = Color(0xFFF3E6D6),
    outline = Color(0xFF33213F),
    muted = Color(0xFF7A6684),
    faint = Color(0xFF7A6684),
    primary = Color(0xFF6B4FC8),
    onPrimary = Color(0xFFFBF3E8),
    select = Color(0xFFFBDA61),
    onSelect = Color(0xFF33213F),
    action = Color(0xFF33213F),
    danger = Color(0xFFB8326A),
    success = Color(0xFF2F7D45),
    shadow = Color(0xFF33213F),
    bandHigh = Color.White.copy(alpha = 0.60f),
    bandLow = Color(0xFF33213F).copy(alpha = 0.25f),
    scrim = Color(0xFF33213F).copy(alpha = 0.55f),
    bestRow = Color(0xFFE3F3E6),
    selBg = Color(0xFFFBDA61),
    selBorder = Color(0xFF33213F),
    selText = Color(0xFF33213F),
    pink = Color(0xFFF2B6C6),
    teal = Color(0xFF9FDCD4),
)
