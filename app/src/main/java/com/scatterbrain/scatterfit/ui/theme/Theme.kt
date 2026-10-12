package com.scatterbrain.scatterfit.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

@Preview(showBackground = true)
@Composable
fun ThemePreview() {
    ScatterFitTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Brand Lime", color = BrandPrimaryLime)
            Text("Judgement Good", color = JudgementGood)
            Text("Judgement Warn", color = JudgementWarn)
            Text("Judgement Bad", color = JudgementBad)
        }
    }
}
