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
        painter = painterResource(id = R.drawable.ic_calendar),
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
    Canvas(modifier = modifier) {
        val strokeWidth = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        // Center barbell line
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Left outer plate
        drawLine(
            color = tint,
            start = Offset(w * 0.22f, h * 0.30f),
            end = Offset(w * 0.22f, h * 0.70f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // Left inner plate
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.20f),
            end = Offset(w * 0.32f, h * 0.80f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Right inner plate
        drawLine(
            color = tint,
            start = Offset(w * 0.68f, h * 0.20f),
            end = Offset(w * 0.68f, h * 0.80f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // Right outer plate
        drawLine(
            color = tint,
            start = Offset(w * 0.78f, h * 0.30f),
            end = Offset(w * 0.78f, h * 0.70f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FlameIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            cubicTo(w * 0.65f, h * 0.35f, w * 0.85f, h * 0.52f, w * 0.85f, h * 0.72f)
            cubicTo(w * 0.85f, h * 0.90f, w * 0.70f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.30f, h * 0.95f, w * 0.15f, h * 0.90f, w * 0.15f, h * 0.72f)
            cubicTo(w * 0.15f, h * 0.52f, w * 0.35f, h * 0.35f, w * 0.5f, h * 0.12f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun FootstepsIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        // Left footprint outline
        val leftPath = Path().apply {
            moveTo(w * 0.30f, h * 0.30f)
            cubicTo(w * 0.40f, h * 0.30f, w * 0.42f, h * 0.65f, w * 0.30f, h * 0.75f)
            cubicTo(w * 0.18f, h * 0.65f, w * 0.20f, h * 0.30f, w * 0.30f, h * 0.30f)
            close()
        }
        // Right footprint outline (higher)
        val rightPath = Path().apply {
            moveTo(w * 0.70f, h * 0.18f)
            cubicTo(w * 0.80f, h * 0.18f, w * 0.82f, h * 0.53f, w * 0.70f, h * 0.63f)
            cubicTo(w * 0.58f, h * 0.53f, w * 0.60f, h * 0.18f, w * 0.70f, h * 0.18f)
            close()
        }
        drawPath(path = leftPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        drawPath(path = rightPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
}

@Composable
fun ProteinIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        // Shaker / jar outline
        val path = Path().apply {
            moveTo(w * 0.30f, h * 0.22f)
            lineTo(w * 0.70f, h * 0.22f)
            lineTo(w * 0.65f, h * 0.88f)
            lineTo(w * 0.35f, h * 0.88f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Cap line
        drawLine(
            color = tint,
            start = Offset(w * 0.25f, h * 0.22f),
            end = Offset(w * 0.75f, h * 0.22f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MoonIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.75f, h * 0.15f)
            cubicTo(w * 0.35f, h * 0.15f, w * 0.15f, h * 0.45f, w * 0.22f, h * 0.80f)
            cubicTo(w * 0.25f, h * 0.95f, w * 0.40f, h * 0.98f, w * 0.50f, h * 0.95f)
            cubicTo(w * 0.28f, h * 0.75f, w * 0.35f, h * 0.35f, w * 0.75f, h * 0.15f)
            close()
        }
        drawPath(path = path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ScaleIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        // Square platform with rounded corners
        val rect = Rect(w * 0.15f, h * 0.18f, w * 0.85f, h * 0.88f)
        val path = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(w * 0.12f, h * 0.12f)))
        }
        drawPath(path = path, color = tint, style = Stroke(width = sw))

        // Center dial top
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.38f, h * 0.26f),
            size = Size(w * 0.24f, h * 0.24f),
            style = Stroke(width = sw)
        )
    }
}

@Composable
fun BedIcon(
    modifier: Modifier = Modifier,
    tint: Color = TextForeground,
    strokeWidthDp: Dp = 1.8.dp
) {
    Canvas(modifier = modifier) {
        val sw = strokeWidthDp.toPx()
        val w = size.width
        val h = size.height

        // Headboard (left vertical post)
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.25f),
            end = Offset(w * 0.15f, h * 0.85f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Footboard (right vertical post)
        drawLine(
            color = tint,
            start = Offset(w * 0.85f, h * 0.50f),
            end = Offset(w * 0.85f, h * 0.85f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Mattress base line
        drawLine(
            color = tint,
            start = Offset(w * 0.15f, h * 0.65f),
            end = Offset(w * 0.85f, h * 0.65f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )

        // Pillow / headrest curve
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.62f)
            cubicTo(w * 0.22f, h * 0.48f, w * 0.38f, h * 0.48f, w * 0.42f, h * 0.62f)
        }
        drawPath(path = path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }
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
