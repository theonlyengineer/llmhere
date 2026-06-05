package dev.edgellm.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val BubbleRadius = 20.dp
private val HardCornerRadius = 4.dp

/**
 * Bubble shape with a hard (small-radius) corner on the sender's side.
 * User messages get a hard corner at bottom-right; assistant at bottom-left.
 */
class MessageBubbleShape(private val isUser: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { BubbleRadius.toPx() }
        val sr = with(density) { HardCornerRadius.toPx() }
        val w = size.width
        val h = size.height

        val tl = r
        val tr = r
        val br = if (isUser) sr else r
        val bl = if (isUser) r else sr

        val path = Path().apply {
            moveTo(tl, 0f)
            lineTo(w - tr, 0f)
            cubicTo(w, 0f, w, 0f, w, tr)
            lineTo(w, h - br)
            cubicTo(w, h, w, h, w - br, h)
            lineTo(bl, h)
            cubicTo(0f, h, 0f, h, 0f, h - bl)
            lineTo(0f, tl)
            cubicTo(0f, 0f, 0f, 0f, tl, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
