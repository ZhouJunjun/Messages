package org.fossify.messages.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import org.fossify.messages.R

/**
 * Puts [text] on the clipboard and confirms it exactly once.
 *
 * 🔴 From Android 13 (API 33) the system renders its own standard "已复制" bubble for *every*
 * clipboard write, so an app toast too means the user sees the confirmation twice - which is the
 * duplicate reported for the copyable-string rows (1.1.06 fix). Google's copy-and-paste guide
 * ("Avoid duplicate notifications") tells apps to drop their own toast on 13+ for exactly this
 * reason, so the toast below is kept only for Android 12L and lower.
 *
 * This replaces `commons`' `Context.copyToClipboard()`, which always toasts and therefore cannot be
 * used here. The clip label matches the one commons uses.
 *
 * This is the single place that writes to the clipboard: the copyable-string rows and the
 * notification's "copy verification code" button (1.1.09 spec) both go through it.
 */
fun copyQuietly(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val label = context.getString(org.fossify.commons.R.string.fossify_commons)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }
}
