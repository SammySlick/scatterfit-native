package com.scatterbrain.scatterfit.ui.MetricPages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.ui.MetricScreen

/**
 * Thin wrapper: the page is the registry entry — see core/MetricRegistry.kt
 * and ui/MetricScreen.kt. Kept so MyDayScreen's routing stays stable.
 */
@Composable
fun WeightScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    MetricScreen(initialId = MetricId.WEIGHT, onBackClick = onBackClick, modifier = modifier)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun WeightScreenPreview() {
    com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme {
        WeightScreen(onBackClick = {})
    }
}
