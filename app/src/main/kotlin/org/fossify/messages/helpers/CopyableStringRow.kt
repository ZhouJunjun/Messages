package org.fossify.messages.helpers

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import org.fossify.commons.extensions.copyToClipboard

/**
 * Renders [items] (already-extracted copyable strings) as a row of transparent "buttons":
 * only the text is colored, a thin vertical bar separates the entries, and the whole row
 * wraps automatically because it is a single [TextView].
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

    val builder = SpannableStringBuilder()
    items.forEachIndexed { index, item ->
        if (index > 0) {
            val sepStart = builder.length
            builder.append(" │ ")
            builder.setSpan(
                ForegroundColorSpan(dividerColor),
                sepStart,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
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
