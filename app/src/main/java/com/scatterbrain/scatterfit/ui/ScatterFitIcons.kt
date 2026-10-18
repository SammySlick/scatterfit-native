package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scatterbrain.scatterfit.R
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
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
fun CalendarIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_calendar_days),
        contentDescription = "Calendar",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun StreakIcon(
    modifier: Modifier = Modifier,
    tint: Color = AccentTeal
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_flame),
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
    tint: Color = AccentTeal
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

@Composable
fun ChevronRightIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_chevron_right),
        contentDescription = "Chevron Next",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun ChevronLeftIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_chevron_left),
        contentDescription = "Chevron Prev",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun DumbbellIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 2.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_dumbbell),
        contentDescription = "Dumbbell",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun FlameIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_flame),
        contentDescription = "Flame",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun FootstepsIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_footprints),
        contentDescription = "Footprints",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun UtensilsIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_utensils),
        contentDescription = "Utensils",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun BeefIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_beef),
        contentDescription = "Beef",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun ProteinIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_beef),
        contentDescription = "Protein",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun MoonIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_moon),
        contentDescription = "Moon",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun ScaleIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_scale),
        contentDescription = "Scale",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun BedIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_bed_double),
        contentDescription = "Bed",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun WineIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_wine),
        contentDescription = "Wine",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun GlassWaterIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_glass_water),
        contentDescription = "Glass Water",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun RefreshIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_refresh_cw),
        contentDescription = "Sync",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun CheckIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_check),
        contentDescription = "Check",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun CloseXIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_x),
        contentDescription = "Close",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun DashedCircleIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val r = (size.minDimension - sw) / 2f
        drawCircle(
            color = tint,
            radius = r,
            center = center,
            style = Stroke(
                width = sw,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
        )
    }
}

@Composable
fun AppleIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        val applePath = Path().apply {
            moveTo(w * 0.5f, h * 0.32f)
            cubicTo(w * 0.65f, h * 0.18f, w * 0.90f, h * 0.30f, w * 0.90f, h * 0.58f)
            cubicTo(w * 0.90f, h * 0.82f, w * 0.70f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.30f, h * 0.95f, w * 0.10f, h * 0.82f, w * 0.10f, h * 0.58f)
            cubicTo(w * 0.10f, h * 0.30f, w * 0.35f, h * 0.18f, w * 0.5f, h * 0.32f)
            close()
        }
        drawPath(path = applePath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val leafPath = Path().apply {
            moveTo(w * 0.5f, h * 0.30f)
            cubicTo(w * 0.55f, h * 0.15f, w * 0.70f, h * 0.12f, w * 0.75f, h * 0.12f)
            cubicTo(w * 0.65f, h * 0.22f, w * 0.58f, h * 0.26f, w * 0.5f, h * 0.30f)
        }
        drawPath(path = leafPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CameraIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        val bodyRect = Rect(w * 0.12f, h * 0.30f, w * 0.88f, h * 0.88f)
        val bodyPath = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(bodyRect, androidx.compose.ui.geometry.CornerRadius(w * 0.08f, h * 0.08f)))
        }
        drawPath(path = bodyPath, color = tint, style = Stroke(width = sw))

        val bumpPath = Path().apply {
            moveTo(w * 0.35f, h * 0.30f)
            lineTo(w * 0.40f, h * 0.18f)
            lineTo(w * 0.60f, h * 0.18f)
            lineTo(w * 0.65f, h * 0.30f)
        }
        drawPath(path = bumpPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))

        drawCircle(
            color = tint,
            radius = w * 0.18f,
            center = Offset(w * 0.5f, h * 0.59f),
            style = Stroke(width = sw)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ScatterFitIconsPreview() {
    com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsIcon()
            StreakIcon()
            PulseIcon()
            HeartPulseIcon()
            LightningIcon()
            CompassIcon()
            AskChatIcon()
        }
    }
}
