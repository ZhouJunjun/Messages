package org.fossify.messages.helpers

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import org.fossify.commons.extensions.copyToClipboard

/**
 * Renders [items] (already-extracted copyable strings) as a row of transparent "buttons":
 * only the text is coloured, and the entries are separated by spacing alone.
 *
 * The separator used to be a "│" glyph, then a real 0.5dp bar. The 1.0.9 spec drops the vertical
 * separator entirely - the spacing that used to flank it is kept, so the entries stay as far apart
 * as they were, just without anything drawn between them. The whole row still wraps automatically
 * because it is a single [TextView].
 *
 * Tapping a string copies it to the clipboard.
 */
fun TextView.setupCopyableStrings(
    context: Context,
    items: List<String>,
    textColor: Int,
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
            builder.append(ITEM_SEPARATOR_TEXT)
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
 * Spaces kept on each side of where the in-row separator used to be. The 1.0.5 spec doubled the
 * horizontal spacing of the copyable-string buttons to two spaces per side; the 1.0.9 spec removes
 * the separator but keeps that spacing, hence four spaces between two entries.
 */
private const val SIDE_SPACES_TEXT = "  "
private const val ITEM_SEPARATOR_TEXT = SIDE_SPACES_TEXT + SIDE_SPACES_TEXT
