package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.messages.helpers.COPY_VERIFICATION_CODE
import org.fossify.messages.helpers.VERIFICATION_CODE
import org.fossify.messages.helpers.copyQuietly

/**
 * Handles the notification's "copy verification code" button (1.1.09 spec): the code travels in the
 * intent, so the receiver does not have to re-read the message or re-run the extraction.
 *
 * The notification itself is intentionally left alone - the code has usually already been copied
 * automatically when the message arrived, and this button is the manual fallback for when the
 * clipboard has been overwritten since.
 */
class CopyVerificationCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != COPY_VERIFICATION_CODE) {
            return
        }

        val code = intent.getStringExtra(VERIFICATION_CODE) ?: return
        copyQuietly(context, code)
    }
}
