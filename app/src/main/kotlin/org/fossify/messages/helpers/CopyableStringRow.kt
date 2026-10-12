package org.fossify.messages.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.fossify.messages.R

/**
 * Renders [items] (already-extracted copyable strings) as a row of transparent "buttons":
 * only the text is coloured, and the entries are separated by spacing alone.
 *
 * The separator used to be a "│" glyph, then a real 0.5dp bar. The 1.0.9 spec drops the vertical
 * separator entirely - the spacing that used to flank it is kept, so the entries stay as far apart
 * as they were, just without anything drawn between them. The whole row still wraps automatically
 * because it is a single [TextView].
 *
 * Tapping a string copies it to the clipboard - once, see [copyQuietly].
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
                copyQuietly(context, item)
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
 * Puts [text] on the clipboard and confirms it exactly once.
 *
 * 🔴 From Android 13 (API 33) the system renders its own standard "已复制" bubble for *every*
 * clipboard write, so an app toast too means the user sees the confirmation twice - which is the
 * duplicate that was reported (1.1.06 fix). Google's copy-and-paste guide ("Avoid duplicate
 * notifications") tells apps to drop their own toast on 13+ for exactly this reason, so the toast
 * below is kept only for Android 12L and lower, where no system confirmation exists.
 *
 * This replaces `commons`' `Context.copyToClipboard()`, which always toasts and therefore cannot be
 * used here. The clip label matches the one commons uses, so the system bubble shows the app name.
 */
private fun copyQuietly(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val label = context.getString(org.fossify.commons.R.string.fossify_commons)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }
}

/**
 * Spaces kept on each side of where the in-row separator used to be. The 1.0.5 spec doubled the
 * horizontal spacing of the copyable-string buttons to two spaces per side; the 1.0.9 spec removes
 * the separator but keeps that spacing, hence four spaces between two entries.
 */
private const val SIDE_SPACES_TEXT = "  "
private const val ITEM_SEPARATOR_TEXT = SIDE_SPACES_TEXT + SIDE_SPACES_TEXT
