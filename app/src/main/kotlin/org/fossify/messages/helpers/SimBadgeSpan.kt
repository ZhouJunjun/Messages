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
 * impossible to read as "SIM 1". This renders a SIM card with the label INSIDE it:
 *
 *  - 1.0.7..1.1.03 the label was the slot digit, centred on the icon;
 *  - 1.1.04 briefly drew the operator's short name (移动 / 联通 / 电信, or 移动1 / 移动2 when two
 *    cards share an operator) BESIDE the icon - wrong call: the name left the badge and floated
 *    loose on the metadata line (1.1.05 fix). Since then the label is drawn centred INSIDE the
 *    icon, and the icon stretches horizontally to fit labels longer than one character;
 *  - 1.1.06 sized that stretch against the *drawable bounds*, but the SIM vector's outline only
 *    covered ~72% of its own viewport horizontally (a wide transparent margin), so a two-character
 *    name came out wider than the visible card and the text ran past its edges. The fix then was
 *    a `1 / 0.7213` compensation, plus a wider left gap to clear the icon's notched top-left
 *    corner;
 *  - 🔴 1.1.07 removed the cause instead: [R.drawable.ic_sim_card_vector] is a plain rounded
 *    rectangle whose outline fills its whole viewport, so the bounds are the card. That kills the
 *    compensation factor AND the asymmetric padding - both inner gaps are [PAD_RATIO] again and
 *    the label is centred in the card.
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

    /** Inner gap on either side of the label - the same on the left and on the right now. */
    private val pad = (sizePx * PAD_RATIO).roundToInt()

    /**
     * Width of the card exactly as the user sees it. Since the outline fills the viewport this is
     * also the width the badge has to occupy.
     */
    private val cardWidth = ceil(labelWidth).roundToInt() + pad * 2

    /** Width of the whole badge; never narrower than it is tall, so a digit stays a square card. */
    private val badgeWidth = max(sizePx, cardWidth)

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
        // word a landscape card of the same height
        icon.setBounds(left, badgeTop, left + badgeWidth, badgeTop + sizePx)
        icon.setTint(iconColor)
        icon.draw(canvas)

        // defensive: if a longer label still exceeds the card, shrink it to fit
        if (labelWidth > cardWidth) {
            labelPaint.textSize = labelPaint.textSize * cardWidth / labelWidth
        }

        val labelX = left + badgeWidth / 2f
        val labelY = centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f
        canvas.drawText(label, labelX, labelY, labelPaint)
    }

    companion object {
        /** Label size relative to the badge size (kept from the digit-only version). */
        private const val LABEL_TEXT_RATIO = 0.62f

        /**
         * Inner gap on each side of the label, relative to the badge height. Equal on both sides
         * since 1.1.07: the notched corner that used to demand a wider left gap is gone.
         */
        private const val PAD_RATIO = 0.24f
    }
}
