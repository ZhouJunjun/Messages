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
 *  - 1.1.04 briefly drew the operator's short name (移动 / 联通 / 电信, or 移动1 / 移动2 when two
 *    cards share an operator) BESIDE the icon - wrong call: the name left the badge and floated
 *    loose on the metadata line (1.1.05 fix). Since then the label is drawn centred INSIDE the
 *    icon, and the icon stretches horizontally to fit labels longer than one character;
 *  - 🔴 1.1.06: the stretching was measured against the *drawable bounds*, but the SIM vector's
 *    outline only covers ~72% of its own viewport horizontally (it has a wide transparent margin,
 *    see [ICON_H_FILL]). A two-character name therefore came out wider than the visible card and
 *    the text ran straight into - and past - its edges. The badge is now sized so the *visible*
 *    card holds the label with an inner gap on both sides, and the gap on the left is the wider
 *    one because the card's top-left corner is notched.
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

    /** Inner gap left of the label - the wider one, to clear the notched top-left corner. */
    private val padLeft = (sizePx * PAD_LEFT_RATIO).roundToInt()

    /** Inner gap right of the label. */
    private val padRight = (sizePx * PAD_RIGHT_RATIO).roundToInt()

    /**
     * Width of the card as the user actually sees it: the icon's outline does not fill the drawable
     * viewport, so the visible card is narrower than the bounds unless we compensate.
     */
    private val visibleCardWidth = ceil(labelWidth).roundToInt() + padLeft + padRight

    /**
     * Width of the whole badge = the drawable bounds. Grown by `1 / ICON_H_FILL` so the *visible*
     * card comes out at [visibleCardWidth]; never narrower than the badge is tall.
     */
    private val badgeWidth = max(sizePx, ceil(visibleCardWidth / ICON_H_FILL).roundToInt())

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

        // the icon stretches to the full badge width - for a digit that is a small square, for a
        // word a landscape card with the same notch at its top-left corner
        icon.setBounds(left, badgeTop, left + badgeWidth, badgeTop + sizePx)
        icon.setTint(iconColor)
        icon.draw(canvas)

        // defensive: if a longer label still exceeds the card, shrink it to fit
        if (labelWidth > visibleCardWidth) {
            labelPaint.textSize = labelPaint.textSize * visibleCardWidth / labelWidth
        }

        // The visible card is centred inside the bounds (the icon's side insets are equal), so the
        // text has to be shifted right by half the padding difference to end up with the wider gap
        // on the left.
        val labelX = left + badgeWidth / 2f + (padLeft - padRight) / 2f
        val labelY = centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f
        canvas.drawText(label, labelX, labelY, labelPaint)
    }

    companion object {
        /** Label size relative to the badge size (kept from the digit-only version). */
        private const val LABEL_TEXT_RATIO = 0.62f

        /** Inner gap on the left, relative to the badge height. */
        private const val PAD_LEFT_RATIO = 0.30f

        /** Inner gap on the right, relative to the badge height. */
        private const val PAD_RIGHT_RATIO = 0.18f

        /**
         * Fraction of the drawable viewport the SIM icon's outline actually covers horizontally:
         * the path runs from x=3.338 to x=20.65 of a 24-unit viewport, i.e. `(20.65 - 3.338) / 24`.
         * Everything outside that is transparent padding, which is why the badge has to be laid out
         * wider than the card it is supposed to show.
         */
        private const val ICON_H_FILL = 0.7213f
    }
}
