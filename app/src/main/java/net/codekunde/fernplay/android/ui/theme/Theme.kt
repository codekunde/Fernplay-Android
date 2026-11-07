package net.codekunde.fernplay.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeChoice { System, Dark, Light }

private val LocalFernplayColors = staticCompositionLocalOf { DarkFernplayColors }

object Fernplay {
    val colors: FernplayColors
        @Composable @ReadOnlyComposable get() = LocalFernplayColors.current
}

@Composable
fun FernplayTheme(choice: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Dark -> true
        ThemeChoice.Light -> false
    }
    val c = if (dark) DarkFernplayColors else LightFernplayColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
            secondary = c.amber, onSecondary = c.onAmber,
            background = c.background, onBackground = c.text,
            surface = c.background, onSurface = c.text,
            surfaceVariant = c.surface, onSurfaceVariant = c.textDim,
            // Dialogs and menus sit on Fernplay's surface, not Material's tinted default.
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
            outline = c.outline,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
            secondary = c.amber, onSecondary = c.onAmber,
            background = c.background, onBackground = c.text,
            surface = c.background, onSurface = c.text,
            surfaceVariant = c.surface, onSurfaceVariant = c.textDim,
            // Dialogs and menus sit on Fernplay's surface, not Material's tinted default.
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
            outline = c.outline,
        )
    }
    CompositionLocalProvider(LocalFernplayColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = FernplayTypography, content = content)
    }
}
