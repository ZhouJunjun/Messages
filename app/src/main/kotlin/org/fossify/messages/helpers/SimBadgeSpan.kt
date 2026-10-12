package org.fossify.messages.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the "which SIM received this" marker on a message's metadata line.
 *
 * It used to be plain text of the form `[1]`, which the 1.0.7 spec called out as ugly and
 * impossible to read as "SIM 1". This renders the SIM card icon with the label INSIDE it:
 *
 *  - 1.0.7..1.1.03 the label was the slot digit, centred on the icon;
 *  - 1.1.04 briefly drew the operator's short name (移动 / 联通 / 电信, or 移动1 / 移动2 when
 *    two cards share an operator) BESIDE the icon - wrong call: the name left the badge and
 *    floated loose on the metadata line (1.1.05 fix). Since then the label is drawn centred
 *    INSIDE the icon again; the icon stretches horizontally to fit labels longer than one
 *    character, keeping its notched-corner card shape.
 *
 * [icon] is expected to already be a private copy (see `mutate()` at the call site) - this span
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
        textSize = sizePx * LABEL_TEXT_RATIO
    }

    /** Width of the label run, measured with the exact paint `draw` uses. */
    private val labelWidth = labelPaint.measureText(label)

    /**
     * Width of the whole badge: the icon itself for a one-character label, stretched wide
     * enough to hold a longer one with a little inner padding on both sides.
     */
    private val badgeWidth = max(
        sizePx,
        ceil(labelWidth).roundToInt() + 2 * (sizePx * LABEL_H_PADDING_RATIO).roundToInt(),
    )

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        return badgeWidth
    }

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

        // the icon stretches to the full badge width - for a digit that is the original square,
        // for a word a landscape card with the same notch at its top-left corner
        icon.setBounds(left, badgeTop, left + badgeWidth, badgeTop + sizePx)
        icon.setTint(iconColor)
        icon.draw(canvas)

        // defensive: if a longer label still exceeds the inner width, shrink it to fit
        val innerWidth = badgeWidth - 2 * (sizePx * LABEL_H_PADDING_RATIO).roundToInt()
        if (labelWidth > innerWidth) {
            labelPaint.textSize = labelPaint.textSize * innerWidth / labelWidth
        }
        val labelY = centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f
        canvas.drawText(label, left + badgeWidth / 2f, labelY, labelPaint)
    }

    companion object {
        /** Label size relative to the badge size (kept from the digit-only version). */
        private const val LABEL_TEXT_RATIO = 0.62f

        /** Inner side padding of the badge, relative to the badge size. */
        private const val LABEL_H_PADDING_RATIO = 0.15f
    }
}
