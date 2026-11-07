package net.codekunde.fernplay.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Fernplay's colour tokens (dark is the default look; light follows the system or Settings). */
@Immutable
data class FernplayColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val title: Color,
    val amber: Color,
    val onAmber: Color,
    val accent: Color,
    val accentLight: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val selected: Color,
    val lcd: Color,
    val lcdBackground: Color,
    val lcdDim: Color,
    /** Second line on the LCD (artist, time left). */
    val lcdSoft: Color,
    /** Rim around the LCD and the mini player. */
    val lcdFrame: Color,
    val isDark: Boolean,
)

val DarkFernplayColors = FernplayColors(
    background = Color(0xFF1A1C23),
    surface = Color(0xFF2A2D38),
    surfaceAlt = Color(0xFF222530),
    outline = Color(0xFF4A4F62),
    text = Color(0xFFD7DAE3),
    textDim = Color(0xFFA0A5B6), // lifted from the desktop's #8E93A3 for 4.5:1 on surfaces
    title = Color(0xFFF2E19A),
    amber = Color(0xFFE8C547),
    onAmber = Color(0xFF08090C),
    accent = Color(0xFF5FA052),
    accentLight = Color(0xFF7FBF6E),
    onAccent = Color(0xFF08090C),
    accentContainer = Color(0xFF2A4E27),
    onAccentContainer = Color(0xFFDCEFD6),
    selected = Color(0xFF25402A),
    lcd = Color(0xFF4BE868),
    lcdBackground = Color(0xFF030604),
    lcdDim = Color(0xFF123A1C),
    lcdSoft = Color(0xFF7FD78F),
    lcdFrame = Color(0xFF2A2D38),
    isDark = true,
)

val LightFernplayColors = FernplayColors(
    background = Color(0xFFF5F6F0),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFECEEE5),
    outline = Color(0xFFB4B9AA),
    text = Color(0xFF1A1C23),
    textDim = Color(0xFF4C5163),
    title = Color(0xFF1A1C23),
    amber = Color(0xFF7A5C00),
    onAmber = Color(0xFFFFFFFF),
    accent = Color(0xFF2F6B2A),
    accentLight = Color(0xFF2F6B2A),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFD5EBC9),
    onAccentContainer = Color(0xFF16330F),
    selected = Color(0xFFDCEED2),
    // The LCD stays dark in both themes, as in the design.
    lcd = Color(0xFF4BE868),
    lcdBackground = Color(0xFF030604),
    lcdDim = Color(0xFF123A1C),
    lcdSoft = Color(0xFF7FD78F),
    lcdFrame = Color(0xFF2A2D38),
    isDark = false,
)
