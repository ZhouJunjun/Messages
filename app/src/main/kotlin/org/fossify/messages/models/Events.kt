package org.fossify.messages.models

class Events {

    /**
     * Asks the conversation lists to refresh.
     *
     * [threadIds] narrows the refresh down to specific threads: it is filled by the callers that
     * know exactly which threads changed (e.g. leaving the thread screen), so the receiver can
     * update those rows incrementally instead of re-running the full telephony scan, which is
     * what used to run on every conversation exit (1.1.04 fix). An empty set keeps the old
     * full-refresh behaviour for the callers that cannot know the affected threads (receivers,
     * bulk operations, settings changes).
     */
    class RefreshConversations(val threadIds: Set<Long> = emptySet())

    class RefreshMessages
}
