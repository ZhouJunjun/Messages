package org.fossify.messages.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt
import org.fossify.commons.extensions.copyToClipboard

/**
 * Renders [items] (already-extracted copyable strings) as a row of transparent "buttons":
 * only the text is coloured, and a vertical bar separates the entries.
 *
 * The separator used to be a "│" text glyph, which rendered noticeably thicker than the
 * horizontal divider above the row. It is now drawn as a real 1dp bar (same colour and same
 * thickness as the horizontal divider), per the 1.0.3 spec. The whole row wraps
 * automatically because it is still a single [TextView].
 *
 * Tapping a string copies it to the clipboard.
 */
fun TextView.setupCopyableStrings(
    context: Context,
    items: List<String>,
    textColor: Int,
    dividerColor: Int,
    fontSizeSp: Float,
) {
    if (items.isEmpty()) {
        visibility = View.GONE
        return
    }

    visibility = View.VISIBLE
    setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)

    val density = context.resources.displayMetrics.density
    val barWidth = (1 * density).roundToInt().coerceAtLeast(1)
    val fontMetrics = paint.fontMetrics
    val barHeight = (fontMetrics.descent - fontMetrics.ascent).roundToInt().coerceAtLeast(barWidth)

    val builder = SpannableStringBuilder()
    items.forEachIndexed { index, item ->
        if (index > 0) {
            appendDividerBar(builder, dividerColor, barWidth, barHeight)
        }

        val start = builder.length
        builder.append(item)
        builder.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                context.copyToClipboard(item)
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = textColor
                ds.isUnderlineText = false
            }
        }, start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    text = builder
    movementMethod = LinkMovementMethod.getInstance()
    isClickable = true
    isFocusable = false
}

/**
 * Appends " ▏ " where the middle glyph is replaced by a 1dp wide, full-line-height bar in
 * [color] - the exact same colour and thickness as the horizontal divider above the row.
 */
private fun appendDividerBar(
    builder: SpannableStringBuilder,
    color: Int,
    widthPx: Int,
    heightPx: Int,
) {
    builder.append(' ')

    val start = builder.length
    builder.append('\u200B')
    val bar = VerticalBarDrawable(color, widthPx, heightPx)
    bar.setBounds(0, 0, widthPx, heightPx)
    builder.setSpan(
        ImageSpan(bar, DynamicDrawableSpan.ALIGN_CENTER),
        start,
        builder.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    builder.append(' ')
}

/** Solid vertical bar used as the in-row separator. */
private class VerticalBarDrawable(
    private val barColor: Int,
    private val widthPx: Int,
    private val heightPx: Int,
) : Drawable() {
    private val paint = Paint().apply {
        isAntiAlias = false
        color = barColor
    }

    override fun draw(canvas: Canvas) {
        val center = bounds.exactCenterX()
        val half = widthPx / 2f
        canvas.drawRect(center - half, bounds.top.toFloat(), center + half, bounds.bottom.toFloat(), paint)
    }

    override fun getIntrinsicWidth() = widthPx

    override fun getIntrinsicHeight() = heightPx

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
