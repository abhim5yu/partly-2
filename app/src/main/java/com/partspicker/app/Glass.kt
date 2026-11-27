package com.partspicker.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How see-through the floating nav bar is: 0 = nearly solid, 1 = clearest glass.
 * Provided once at the app root from the saved setting.
 */
val LocalNavTransparency = compositionLocalOf { 0.5f }

/**
 * Floating "glass" capsule for navigation controls only (never for cards or lists).
 * Neutral tint that follows light/dark, a thin specular rim (bright on top, softer
 * below) and a faint top sheen. Compose can't blur what's behind a view, so the
 * see-through comes from the adjustable translucent tint.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    transparency: Float = LocalNavTransparency.current,
    content: @Composable () -> Unit
) {
    val c = LocalAppColors.current
    val dark = c.bg.luminance() < 0.5f
    val t = transparency.coerceIn(0f, 1f)
    val fill = (if (dark) Color(0xFF1B1814) else Color.White).copy(alpha = 0.94f - 0.82f * t)
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.45f), Color.Transparent)
    )
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.60f else 0.95f),
            Color.White.copy(alpha = if (dark) 0.08f else 0.20f),
            if (dark) Color.White.copy(alpha = 0.28f) else c.text.copy(alpha = 0.18f)
        )
    )
    Box(
        modifier
            .lift(PillShape, 8.dp)
            .clip(PillShape)
            .background(fill)
            .background(sheen)
            .border(1.dp, rim, PillShape)
            .padding(5.dp)
    ) {
        content()
    }
}

/** A button inside a [GlassPill]: springs down and lights up while pressed. */
@Composable
fun GlassButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val dark = c.bg.luminance() < 0.5f
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "glassScale"
    )
    val glow by animateFloatAsState(targetValue = if (pressed) 1f else 0f, label = "glassGlow")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(PillShape)
            .background(Color.White.copy(alpha = glow * (if (dark) 0.16f else 0.55f)))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .height(48.dp)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
        Text(label, color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Settings card: live preview of the nav bar over a colourful strip + transparency slider. */
@Composable
fun NavGlassCard(transparency: Float, onChange: (Float) -> Unit, onChangeFinished: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .lift(RadiusXl)
            .clip(RadiusXl)
            .background(c.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(Radius)
                .background(Brush.horizontalGradient(listOf(c.accent, Color(0xFF6C8CFF), c.success))),
            contentAlignment = Alignment.Center
        ) {
            GlassPill(transparency = transparency) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GlassButton("Sheets", Icons.Filled.TableChart, {})
                    GlassButton("Settings", Icons.Filled.Settings, {})
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Transparency", color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("${(transparency * 100).toInt()}%", color = c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Slider(
            value = transparency,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.surface2
            )
        )
    }
}
