package com.tokopad.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TokopadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF165C46), onPrimary = Color.White,
            primaryContainer = Color(0xFFD4EE94), onPrimaryContainer = Color(0xFF203716),
            secondary = Color(0xFF52665A), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEBF0E6), onSecondaryContainer = Color(0xFF324536),
            tertiary = Color(0xFF72582D), tertiaryContainer = Color(0xFFF5EBD7),
            background = Color(0xFFF7F8F2), onBackground = Color(0xFF192B25),
            surface = Color.White, onSurface = Color(0xFF192B25),
            surfaceVariant = Color(0xFFF0F3EB), onSurfaceVariant = Color(0xFF65736A),
            surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFF8FAF5),
            surfaceContainerHigh = Color(0xFFF0F3EB), surfaceContainerHighest = Color(0xFFEAF0E5),
            surfaceTint = Color.Transparent, outline = Color(0xFF89958B), outlineVariant = Color(0xFFE4E8E0),
            error = Color(0xFFA43232), errorContainer = Color(0xFFFBEAE7),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
            headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}

@Composable
fun PadIcon(name: String, modifier: Modifier = Modifier.size(22.dp), tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.7f, StrokeCap.Round)
            fun path(vararg points: Pair<Float, Float>) {
                val p = Path().apply { points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) } }
                drawPath(p, tint, style = Stroke(1.7f, cap = StrokeCap.Round))
            }
            fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(2f), style = Stroke(1.7f))
            when (name) {
                "home" -> { path(3f to 10f, 12f to 3f, 21f to 10f); path(5f to 9f, 5f to 21f, 19f to 21f, 19f to 9f); path(9f to 21f, 9f to 14f, 15f to 14f, 15f to 21f) }
                "checkout" -> { box(4f, 3f, 16f, 18f); line(8f, 7f, 16f, 7f); line(8f, 12f, 10f, 12f); line(14f, 12f, 16f, 12f); line(8f, 16f, 10f, 16f); line(14f, 16f, 16f, 16f) }
                "history", "receipt" -> { path(5f to 3f, 19f to 3f, 19f to 21f, 15.5f to 19f, 12f to 21f, 8.5f to 19f, 5f to 21f, 5f to 3f); line(9f, 8f, 15f, 8f); line(9f, 12f, 15f, 12f) }
                "products" -> { path(3f to 7f, 12f to 3f, 21f to 7f, 21f to 17f, 12f to 21f, 3f to 17f, 3f to 7f, 12f to 11f, 21f to 7f); line(12f, 11f, 12f, 21f); line(7.5f, 5f, 16.5f, 9f) }
                "customers" -> { drawCircle(tint, 3.5f, Offset(9f, 7f), style = Stroke(1.7f)); path(2f to 21f, 2f to 18f, 4f to 14f, 14f to 14f, 16f to 18f, 16f to 21f); path(17f to 4f, 20f to 6f, 20f to 10f, 17f to 12f); path(19f to 15f, 22f to 18f, 22f to 21f) }
                "settings" -> { line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f); box(7f, 4f, 3f, 4f); box(14f, 10f, 3f, 4f); box(8f, 16f, 3f, 4f) }
                "search" -> { drawCircle(tint, 7f, Offset(10f, 10f), style = Stroke(1.7f)); line(15f, 15f, 21f, 21f) }
                "calendar" -> { box(3f, 5f, 18f, 16f); line(3f, 10f, 21f, 10f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f) }
                "arrow" -> { line(4f, 12f, 20f, 12f); path(14f to 6f, 20f to 12f, 14f to 18f) }
                "chevron" -> path(9f to 6f, 15f to 12f, 9f to 18f)
                "close" -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                "check" -> path(5f to 12f, 10f to 17f, 20f to 6f)
                "plus" -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
                "minus" -> line(5f, 12f, 19f, 12f)
                else -> { box(4f, 4f, 6f, 6f); box(14f, 4f, 6f, 6f); box(4f, 14f, 6f, 6f); box(14f, 14f, 6f, 6f) }
            }
        }
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { PadIcon(icon, Modifier.size(24.dp)) }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
