package com.scatterbrain.scatterfit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class JudgmentColors(
    val good: Color,
    val warn: Color,
    val bad: Color,
    val destructive: Color,
    val hot: Color
) {
    val onTrack: Color get() = good
    val watch: Color get() = warn
    val offTrack: Color get() = bad
}

val LocalJudgmentColors = staticCompositionLocalOf {
    JudgmentColors(
        good = JudgementGood,
        warn = JudgementWarn,
        bad = JudgementBad,
        destructive = JudgementDestructive,
        hot = AccentHot
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryLime,
    onPrimary = OnPrimaryText,
    primaryContainer = BrandPrimaryLime.copy(alpha = 0.15f),
    onPrimaryContainer = BrandPrimaryLime,
    secondary = SecondaryFill,
    onSecondary = TextForeground,
    background = AppBackground,
    onBackground = TextForeground,
    surface = AppCardSurface,
    onSurface = TextForeground,
    surfaceVariant = MutedFill,
    onSurfaceVariant = TextMuted,
    outline = BorderDefault
)

@Composable
fun ScatterFitTheme(
    content: @Composable () -> Unit
) {
    val judgmentColors = JudgmentColors(
        good = JudgementGood,
        warn = JudgementWarn,
        bad = JudgementBad,
        destructive = JudgementDestructive,
        hot = AccentHot
    )

    CompositionLocalProvider(
        LocalJudgmentColors provides judgmentColors
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}

object ScatterFitTheme {
    val judgmentColors: JudgmentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJudgmentColors.current
}
