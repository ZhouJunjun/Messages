package org.fossify.messages.models

/**
 * Thread item representations for the main thread recyclerview. [Message] is also a [ThreadItem]
 *
 * The "message sent" check mark used to be its own item row; it is rendered inside the message's
 * metadata line now (see org.fossify.messages.adapters.ThreadAdapter), so no item type is needed
 * for it any more (1.0.4 spec).
 */
sealed class ThreadItem {
    data class ThreadError(val messageId: Long, val messageText: String) : ThreadItem()
    data class ThreadSending(val messageId: Long) : ThreadItem()
}
