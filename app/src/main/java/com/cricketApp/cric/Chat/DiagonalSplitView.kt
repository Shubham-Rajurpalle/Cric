package com.cricketApp.cric.Chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Draws a solid colored triangle covering the LEFT portion of the card.
 * The triangle goes: top-left corner → top-right (partial) → bottom-middle → bottom-left corner
 * This creates the diagonal split from top-left to bottom-center.
 */
class DiagonalSplitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // The color of the left/bottom triangle (team color)
    var splitColor: Int = Color.parseColor("#004BA0")
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    // How far across the top edge the diagonal starts (0.0 = left, 1.0 = right)
    // 0.6 means the diagonal line starts 60% from left on the top edge
    var splitRatioTop: Float = 0.62f
        set(value) {
            field = value
            invalidate()
        }

    // How far across the bottom edge the diagonal ends (0.0 = left, 1.0 = right)
    // 0.5 means the diagonal ends at the middle of the bottom edge
    var splitRatioBottom: Float = 0.50f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Triangle: top-left → top-right(partial) → bottom-middle → bottom-left
        path.reset()
        path.moveTo(0f, 0f)                          // top-left
        path.lineTo(w * splitRatioTop, 0f)           // top edge point (~60% across)
        path.lineTo(w * splitRatioBottom, h)         // bottom edge point (~50% across = middle)
        path.lineTo(0f, h)                           // bottom-left
        path.close()

        canvas.drawPath(path, paint)
    }
}