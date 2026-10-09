package com.eko.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Skin only — swap this file to restyle without touching data or sync.
object C {
    val Ink = Color(0xFF0A1113)
    val Ink2 = Color(0xFF0F1F23)
    val Glass = Color(0x0FFFFFFF)
    val GlassEdge = Color(0x1AFFFFFF)
    val Text = Color(0xFFE8F1EF)
    val Muted = Color(0xFF8FA3A0)
    val Faint = Color(0xFF52625F)
    val Mint = Color(0xFF5EE6B0)
    val Coral = Color(0xFFFF7A6B)
    val Amber = Color(0xFFFFC857)
}

val Backdrop = Brush.verticalGradient(listOf(C.Ink2, C.Ink, C.Ink))
val HeroGlow = Brush.linearGradient(listOf(Color(0x335EE6B0), Color(0x14FF7A6B), Color(0x08FFFFFF)))

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Mint, onPrimary = C.Ink, secondary = C.Coral,
            background = C.Ink, surface = C.Ink2, onSurface = C.Text, onBackground = C.Text,
            surfaceVariant = Color(0xFF16272B), onSurfaceVariant = C.Muted, outline = C.Faint,
        ),
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fill: Brush? = null,
    pad: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .then(if (fill != null) Modifier.background(fill) else Modifier.background(C.Glass))
            .border(1.dp, C.GlassEdge, shape)
            .padding(pad),
        content = content,
    )
}
