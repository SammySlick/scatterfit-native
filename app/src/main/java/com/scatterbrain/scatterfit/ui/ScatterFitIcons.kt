package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scatterbrain.scatterfit.R
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground

@Composable
fun SettingsIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_settings),
        contentDescription = "Settings",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun StreakIcon(
    modifier: Modifier = Modifier,
    tint: Color = BrandPrimaryLime
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_streak),
        contentDescription = "Streak",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun PulseIcon(
    modifier: Modifier = Modifier,
    tint: Color = TealHighlight,
    strokeWidthDp: Dp = 2.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_activity),
        contentDescription = "Pulse",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun HeartPulseIcon(
    modifier: Modifier = Modifier,
    tint: Color = TealHighlight
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_heart_pulse),
        contentDescription = "Heart Pulse",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun LightningIcon(
    modifier: Modifier = Modifier,
    tint: Color = BrandPrimaryLime
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_lightning),
        contentDescription = "Lightning",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun CompassIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Gray,
    strokeWidthDp: Dp = 1.5.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_compass),
        contentDescription = "Compass",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun AskChatIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Gray,
    strokeWidthDp: Dp = 1.5.dp
) {
    Canvas(modifier = modifier) {
        val strokeWidth = strokeWidthDp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f - size.height * 0.05f)
        val rx = size.width * 0.38f
        val ry = size.height * 0.32f

        val path = Path().apply {
            addOval(Rect(center.x - rx, center.y - ry, center.x + rx, center.y + ry))
            moveTo(center.x - rx * 0.4f, center.y + ry * 0.8f)
            lineTo(center.x - rx * 0.8f, center.y + ry * 1.3f)
            lineTo(center.x - rx * 0.1f, center.y + ry * 0.95f)
        }

        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
