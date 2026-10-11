package org.fossify.messages.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * A span that draws nothing but occupies exactly [widthPx] pixels.
 *
 * Used to space out the parts of the thread metadata line - the SIM label, the sender number
 * and the timestamp - by 30dp (1.0.4 spec). A [ReplacementSpan] keeps the whole line a single
 * TextView, so it still ellipsizes / wraps like normal text.
 */
class GapSpan(private val widthPx: Int) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = widthPx

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) = Unit
}
