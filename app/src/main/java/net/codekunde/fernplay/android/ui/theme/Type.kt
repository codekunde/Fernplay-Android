package net.codekunde.fernplay.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.codekunde.fernplay.android.R

/** The green display's font: only for the Now Playing LCD and the mini player. */
val LcdFont = FontFamily(Font(R.font.share_tech_mono))

/** Body text uses the system font (Roboto on most phones). */
val FernplayTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

object LcdText {
    val title = TextStyle(fontFamily = LcdFont, fontSize = 24.sp, lineHeight = 28.sp)
    val time = TextStyle(fontFamily = LcdFont, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = 1.sp)
    val mini = TextStyle(fontFamily = LcdFont, fontSize = 17.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = LcdFont, fontSize = 13.sp, lineHeight = 16.sp)
}
