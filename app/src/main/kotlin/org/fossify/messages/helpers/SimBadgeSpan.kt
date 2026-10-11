package org.fossify.messages.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

/**
 * Draws the "which SIM received this" marker on a message's metadata line.
 *
 * It used to be plain text of the form `[1]`, which the 1.0.7 spec called out as ugly and
 * impossible to read as "SIM 1". This renders the same `SIM icon + slot digit` badge that the
 * bottom bar's operator selector uses, so the two read as one and the same thing.
 *
 * [icon] is expected to already be a private copy (see `mutate()` at the call site) — this span
 * tints it, and tinting a shared Drawable would leak into every other row.
 */
class SimBadgeSpan(
    private val icon: Drawable,
    private val label: String,
    private val iconColor: Int,
    private val labelColor: Int,
    private val sizePx: Int,
) : ReplacementSpan() {

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = sizePx

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        // `y` is the baseline; centre the badge on the text run's visual middle.
        val fm = paint.fontMetrics
        val centerY = y + (fm.ascent + fm.descent) / 2f
        val left = x.roundToInt()
        val badgeTop = (centerY - sizePx / 2f).roundToInt()

        icon.setBounds(left, badgeTop, left + sizePx, badgeTop + sizePx)
        icon.setTint(iconColor)
        icon.draw(canvas)

        labelPaint.textSize = sizePx * 0.62f
        val labelY = centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f
        canvas.drawText(label, left + sizePx / 2f, labelY, labelPaint)
    }
}
