package com.kostaking.raspored.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kostaking.raspored.ui.theme.LocalExtraColors
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)

/** Black "space" backdrop with stars and two faint nebulae (dark theme); soft gradient in light theme. */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val extra = LocalExtraColors.current
    if (!extra.isDark) {
        Box(
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFF7F8FD), Color(0xFFEDEFF8)))),
            content = content,
        )
        return
    }
    val stars = remember {
        val rnd = Random(42)
        List(170) { Star(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 1.6f, 0.15f + rnd.nextFloat() * 0.6f) }
    }
    Box(modifier.fillMaxSize().background(Color(0xFF020309))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x332F3DB8), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.12f),
                    radius = size.width * 0.9f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x2A7B2FB8), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.7f),
                    radius = size.width * 0.8f,
                ),
            )
            stars.forEach { s ->
                drawCircle(Color.White.copy(alpha = s.a), radius = s.r, center = Offset(s.x * size.width, s.y * size.height))
            }
        }
        content()
    }
}
