package com.papertrader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AccentWhite,
    onPrimary = BackgroundBlack,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCardElevated,
    onSurfaceVariant = TextSecondary,
    error = LossRed,
    outline = Divider
)

/**
 * The app is dark-mode-only by product design (a professional FinTech
 * black theme), regardless of the system theme setting.
 */
@Composable
fun PaperTraderTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() intentionally unused for color selection: this
    // app always renders the dark trading theme per the design spec.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = DarkColors,
        typography = PaperTraderTypography,
        content = content
    )
}
