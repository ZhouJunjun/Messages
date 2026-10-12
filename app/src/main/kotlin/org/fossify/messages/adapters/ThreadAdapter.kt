package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlin.math.roundToInt
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.usableScreenSize
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.NewConversationActivity
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.activities.VCardViewerActivity
import org.fossify.messages.databinding.ItemAttachmentDocumentBinding
import org.fossify.messages.databinding.ItemAttachmentImageBinding
import org.fossify.messages.databinding.ItemAttachmentAudioBinding
import org.fossify.messages.databinding.ItemAttachmentVcardBinding
import org.fossify.messages.databinding.ItemMessageBinding
import org.fossify.messages.databinding.ItemThreadErrorBinding
import org.fossify.messages.databinding.ItemThreadSendingBinding
import org.fossify.messages.dialogs.DeleteConfirmationDialog
import org.fossify.messages.dialogs.MessageDetailsDialog
import org.fossify.messages.dialogs.SelectTextDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.isImageMimeType
import org.fossify.messages.extensions.isPlayableAudioMimeType
import org.fossify.messages.extensions.isVCardMimeType
import org.fossify.messages.extensions.isVideoMimeType
import org.fossify.messages.extensions.launchViewIntent
import org.fossify.messages.extensions.starMessage
import org.fossify.messages.extensions.subscriptionManagerCompat
import org.fossify.messages.extensions.unstarMessage
import org.fossify.messages.helpers.EXTRA_VCARD_URI
import org.fossify.messages.helpers.GapSpan
import org.fossify.messages.helpers.SimBadgeSpan
import org.fossify.messages.helpers.THREAD_RECEIVED_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_ERROR
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_SENDING
import org.fossify.messages.helpers.extractCopyableStrings
import org.fossify.messages.helpers.formatMessageDateTime
import org.fossify.messages.helpers.generateStableId
import org.fossify.messages.helpers.linkifyUrls
import org.fossify.messages.helpers.setupAudio
import org.fossify.messages.helpers.setupCopyableStrings
import org.fossify.messages.helpers.setupDocumentPreview
import org.fossify.messages.helpers.setupVCardPreview
import org.fossify.messages.models.Attachment
import org.fossify.messages.models.Message
import org.fossify.messages.models.ThreadItem
import org.fossify.messages.models.ThreadItem.ThreadError
import org.fossify.messages.models.ThreadItem.ThreadSending
import org.joda.time.DateTime

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    val deleteMessages: (messages: List<Message>) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {
    private var fontSize = activity.getTextSize()

    /**
     * Ids of every starred message of this thread, resolved by the activity on a background
     * thread. Setting it rebinds the visible rows: the per-message star toggle (1.1.04 spec)
     * draws its colour from this set, so without the rebind a row bound before the async load
     * finished kept the wrong state until it was scrolled away (review M2).
     */
    var starredIds: Set<Long> = emptySet()
        set(value) {
            if (field != value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }
        }

    /**
     * Message the thread was opened at - the one a starred row or a search result points at. Its
     * bubble gets an extra highlight layer, so the jump target is obvious once the list has
     * scrolled to it (1.0.9 spec). -1 means "no highlighted message".
     */
    var highlightedMessageId: Long = -1L

    /** subscriptionId -> displayed SIM slot number ("1", "2", ...), filled by the activity. */
    var simIdMap: Map<Int, String> = emptyMap()

    /**
     * messageId -> delivered flag for the sent messages that reached the "sent" state. Filled by
     * the activity; the check mark is rendered inside the message's metadata line (1.0.4 spec).
     */
    var sentStatus: Map<Long, Boolean> = emptyMap()

    /**
     * True when every message of the thread comes from the same counterpart as the toolbar title
     * (a one-to-one conversation). The sender number is then redundant on every metadata line and
     * is left out (1.0.5 spec). Group conversations keep it, since senders differ.
     */
    var hideSenderNumber: Boolean = false

    @SuppressLint("MissingPermission")
    private val hasMultipleSIMCards = (activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0) > 1
    private val maxChatBubbleWidth = (activity.usableScreenSize.x * 0.8f).toInt()

    companion object {
        private const val MAX_MEDIA_HEIGHT_RATIO = 3

        /** Gap between the SIM label, the sender number and the timestamp in the metadata line. */
        private const val METADATA_PART_GAP_DP = 10

        /** Opacity of the highlight layer laid over the bubble a jump landed on (1.0.9 spec; 30% -> 20% in 1.1.01). */
        private const val HIGHLIGHT_ALPHA = 0.2f

        /** Star colour of a starred message (1.1.01 spec); unstarred uses the border grey. */
        private const val STAR_FILLED_COLOR = 0xFFFFD600.toInt()

        /** Icon font in `app/src/main/assets/`, holding the single U+2B50 star glyph (1.1.02 rework). */
        private const val STAR_FONT_ASSET = "fonts/MyTree-Regular.ttf"

        /** The glyph the font above is built for - a text star, tinted per state rather than painted. */
        private const val STAR_GLYPH = "\u2B50"
    }

    /**
     * The icon font that draws the star glyph, parsed once and reused: `createFromAsset()` reloads
     * the file on every call and rows are re-bound while scrolling (1.1.02 second rework).
     */
    private val starTypeface: Typeface by lazy {
        Typeface.createFromAsset(activity.assets, STAR_FONT_ASSET)
    }

    init {
        setupDragListener(true)
        setHasStableIds(true)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    override fun getActionMenuId() = R.menu.cab_thread

    override fun prepareActionMode(menu: Menu) {
        val isOneItemSelected = isOneItemSelected()
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        val hasText = selectedMessages.any { it.body.isNotEmpty() }
        val showSaveAs = getSelectedItems().all {
            it is Message && (it.attachment?.attachments?.size ?: 0) > 0
        } && getSelectedAttachments().isNotEmpty()

        menu.apply {
            findItem(R.id.cab_copy_to_clipboard).isVisible = hasText
            findItem(R.id.cab_save_as).isVisible = showSaveAs
            findItem(R.id.cab_share).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_forward_message).isVisible = isOneItemSelected
            findItem(R.id.cab_select_text).isVisible = isOneItemSelected && hasText
            findItem(R.id.cab_properties).isVisible = isOneItemSelected

            val selectedMessage = selectedMessages.firstOrNull()
            val isStarred = selectedMessage != null && starredIds.contains(selectedMessage.id)
            findItem(R.id.cab_star).isVisible = isOneItemSelected && selectedMessage != null
            findItem(R.id.cab_star).title = activity.getString(
                if (isStarred) R.string.unstar_message else R.string.star_message
            )
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_text -> selectText()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_star -> toggleStarred()
            R.id.cab_select_all -> selectAll()
            R.id.cab_properties -> showMessageDetails()
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int): Int? {
        return (currentList.getOrNull(position) as? Message)?.getSelectionKey()
    }

    override fun getItemKeyPosition(key: Int): Int {
        return currentList.indexOfFirst { (it as? Message)?.getSelectionKey() == key }
    }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            THREAD_SENT_MESSAGE_ERROR -> ItemThreadErrorBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENDING -> ItemThreadSendingBinding.inflate(layoutInflater, parent, false)
            else -> ItemMessageBinding.inflate(layoutInflater, parent, false)
        }

        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isClickable = item is ThreadError || item is Message
        val isLongClickable = item is Message
        holder.bindView(item, isClickable, isLongClickable) { itemView, _ ->
            when (item) {
                is ThreadError -> setupThreadError(itemView)
                is ThreadSending -> setupThreadSending(itemView)
                is Message -> setupView(holder, itemView, item)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is Message -> item.getStableId()
            is ThreadError -> generateStableId(THREAD_SENT_MESSAGE_ERROR, item.messageId)
            is ThreadSending -> generateStableId(THREAD_SENT_MESSAGE_SENDING, item.messageId)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
        }
    }

    private fun copyToClipboard() {
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        if (selectedMessages.isEmpty()) return

        val textToCopy = if (selectedMessages.size == 1) {
            selectedMessages.first().body
        } else {
            selectedMessages.filter { it.body.isNotEmpty() }.joinToString("\n\n") { message ->
                val format = "${activity.config.dateFormat}, ${activity.getTimeFormat()}"
                val dateTime = DateTime(message.millis()).toString(format)
                val sender = if (message.isReceivedMessage()) message.senderName else activity.getString(R.string.me)
                "[$dateTime] $sender: ${message.body}"
            }
        }

        if (textToCopy.isNotEmpty()) {
            activity.copyToClipboard(textToCopy)
        }
    }

    private fun getSelectedAttachments(): List<Attachment> {
        val selectedMessages = getSelectedItems().filterIsInstance<Message>()
        return selectedMessages.flatMap { it.attachment?.attachments.orEmpty() }
    }

    private fun saveAs() {
        val attachments = getSelectedAttachments()
        if (attachments.isNotEmpty()) {
            (activity as ThreadActivity).saveMMS(attachments)
        }
    }

    private fun shareText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        activity.shareTextIntent(firstItem.body)
    }

    private fun selectText() {
        val firstItem = getSelectedItems().firstOrNull() as? Message ?: return
        if (firstItem.body.trim().isNotEmpty()) {
            SelectTextDialog(activity, firstItem.body)
        }
    }

    private fun showMessageDetails() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        MessageDetailsDialog(activity, message)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size

        // not sure how we can get UnknownFormatConversionException here, so show the error and hope that someone reports it
        val items = try {
            resources.getQuantityString(R.plurals.delete_messages, itemsCnt, itemsCnt)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            return
        }

        val question = String.format(resources.getString(org.fossify.commons.R.string.deletion_confirmation), items)

        DeleteConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                val messagesToRemove = getSelectedItems()
                if (messagesToRemove.isNotEmpty()) {
                    deleteMessages(messagesToRemove.filterIsInstance<Message>())
                }
            }
        }
    }

    private fun toggleStarred() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        // The in-memory set is the authority here - re-querying the database just to decide
        // which way to flip is a needless main-thread-adjacent read (1.1.04 fix).
        val isStarred = starredIds.contains(message.id)
        starredIds = if (isStarred) {
            starredIds - message.id
        } else {
            starredIds + message.id
        }
        ensureBackgroundThread {
            if (isStarred) {
                activity.unstarMessage(message.id)
            } else {
                activity.starMessage(message.id)
            }
        }
        activity.runOnUiThread {
            finishActMode()
        }
    }

    /**
     * The per-message star toggle on the metadata line (1.1.04 spec): flips the in-memory id
     * set (which rebinds the row) and writes the change on a background thread - `starred_
     * messages` is a plain Room database without `allowMainThreadQueries()`, so the write must
     * leave the main thread (1.1.02 lesson).
     */
    private fun toggleStar(message: Message) {
        val isStarred = starredIds.contains(message.id)
        starredIds = if (isStarred) {
            starredIds - message.id
        } else {
            starredIds + message.id
        }
        ensureBackgroundThread {
            if (isStarred) {
                activity.unstarMessage(message.id)
            } else {
                activity.starMessage(message.id)
            }
        }
    }

    private fun forwardMessage() {
        val message = getSelectedItems().firstOrNull() as? Message ?: return
        val attachment = message.attachment?.attachments?.firstOrNull()
        Intent(activity, NewConversationActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.body)

            if (attachment != null) {
                putExtra(Intent.EXTRA_STREAM, attachment.getUri())
            }

            activity.startActivity(this)
        }
    }

    private fun getSelectedItems(): ArrayList<ThreadItem> {
        return currentList.filter {
            selectedKeys.contains((it as? Message)?.getSelectionKey() ?: 0)
        } as ArrayList<ThreadItem>
    }

    /**
     * Replaces the displayed messages.
     *
     * [scrollPosition] is an adapter index; `-1` leaves the scroll position alone.
     * **Never** pass `smoothScroll = true` for a long hop - see the note in [ThreadActivity]'s
     * `placeMessageInViewport` (1.1.05). Reserved for short, in-viewport animations.
     * [scrollOffset] > 0 puts the row that many pixels below the top of the list, instead of
     * gluing it to the very edge.
     */
    fun updateMessages(
        newMessages: List<ThreadItem>,
        scrollPosition: Int = -1,
        smoothScroll: Boolean = false,
        scrollOffset: Int = 0
    ) {
        val latestMessages = newMessages.toMutableList()
        submitList(latestMessages) {
            if (scrollPosition != -1) {
                if (smoothScroll) {
                    recyclerView.smoothScrollToPosition(scrollPosition)
                } else {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    if (scrollOffset > 0 && layoutManager != null) {
                        layoutManager.scrollToPositionWithOffset(scrollPosition, scrollOffset)
                    } else {
                        recyclerView.scrollToPosition(scrollPosition)
                    }
                }
            }
        }
    }

    private fun setupView(holder: ViewHolder, view: View, message: Message) {
        ItemMessageBinding.bind(view).apply {
            threadMessageHolder.isSelected = selectedKeys.contains(message.getSelectionKey())

            val config = activity.config
            val received = message.isReceivedMessage()

            // The first message keeps a 20dp gap to the toolbar; every following message is
            // separated from the previous one by 40dp (1.0.4 spec).
            val isFirstMessage = holder.absoluteAdapterPosition == 0
            applyHolderTopMargin(threadMessageHolder, if (isFirstMessage) 20 else 40)

            threadMessageMetadata.apply {
                beVisible()
                text = buildMetadataText(message)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.threadInfoTextSize.toFloat())
                setTextColor(config.threadInfoTextColor)

                // the row itself is wrap_content and follows the wrapper's gravity (set in
                // setupReceived/SetupSentMessageView); this per-text gravity is redundant since
                // 1.1.05 but kept harmless
                gravity = if (received) Gravity.START else Gravity.END

                // sent messages carry the "sent" (single tick) / "delivered" (double tick) mark at
                // the end of the first line, tinted with the accent colour (1.0.7 spec)
                val delivered = if (received) null else sentStatus[message.id]
                val checkDrawable = delivered?.let {
                    AppCompatResources.getDrawable(
                        activity,
                        if (it) R.drawable.ic_check_double_vector else org.fossify.commons.R.drawable.ic_check_vector
                    )?.mutate()?.apply {
                        applyColorFilter(config.highlightColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }
                }
                setCompoundDrawablesRelative(null, null, checkDrawable, null)
            }

            // Per-message star toggle (1.1.05 spec): received messages carry it right after the
            // metadata text, sent ones right before it - 10dp away, the same gap the metadata
            // parts keep between themselves. Scheduled entries are skipped - they are not
            // persisted in the telephony store yet, so there is nothing to star.
            val starView = if (received) threadMessageStarEnd else threadMessageStarStart
            val idleStarView = if (received) threadMessageStarStart else threadMessageStarEnd
            idleStarView.beGone()
            idleStarView.setOnClickListener(null)
            starView.apply {
                val canStar = !message.isScheduled
                beVisibleIf(canStar)
                if (!canStar) {
                    setOnClickListener(null)
                } else {
                    // 🔴 Same emoji2 opt-out as the search rows: U+2B50 is an emoji code point
                    // and emoji2 would hand it to the system emoji font, ignoring both the
                    // bundled typeface and the tint (1.1.02 lesson).
                    (this as? AppCompatTextView)?.setEmojiCompatEnabled(false)
                    text = STAR_GLYPH
                    typeface = starTypeface
                    // the spec: the star uses the message body's own font size
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.threadSnippetTextSize.toFloat())
                    setTextColor(
                        if (starredIds.contains(message.id)) STAR_FILLED_COLOR
                        else activity.getColor(org.fossify.commons.R.color.divider_grey)
                    )
                    setOnClickListener {
                        toggleStar(message)
                    }
                }
            }

            threadMessageBody.apply {
                // links are declared here instead of via android:autoLink: the framework pattern
                // rejects hyphens inside a domain label, which left `https://1-` uncoloured
                // (1.0.8 spec)
                text = linkifyUrls(message.body)
                movementMethod = LinkMovementMethod.getInstance()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.threadSnippetTextSize.toFloat())
                beVisibleIf(message.body.isNotEmpty())
                setOnLongClickListener {
                    holder.viewLongClicked()
                    true
                }

                setOnClickListener {
                    holder.viewClicked(message)
                }
            }

            if (received) {
                setupReceivedMessageView(messageBinding = this, message = message)
            } else {
                setupSentMessageView(messageBinding = this, message = message)
            }

            // copyable-string row: only for received messages, per the agreed design
            val copyables = if (received) extractCopyableStrings(message.body) else emptyList()
            // Dividers use the same colour as the toolbar's bottom border (1.0.5 spec).
            val dividerColor = activity.getColor(org.fossify.commons.R.color.divider_grey)
            threadMessageCopyablesDivider.apply {
                beVisibleIf(copyables.isNotEmpty())
                setBackgroundColor(dividerColor)
            }
            threadMessageCopyables.setupCopyableStrings(
                context = activity,
                items = copyables,
                textColor = config.copyableTextColor,
                fontSizeSp = config.copyableTextSize.toFloat()
            )

            threadMessageAttachmentsHolder.removeAllViews()
            if (message.attachment?.attachments?.isNotEmpty() == true) {
                threadMessageAttachmentsHolder.beVisible()
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> setupImageView(holder, binding = this, message, attachment)
                        mimetype.isPlayableAudioMimeType() ->
                            setupAudioView(holder, threadMessageAttachmentsHolder, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, threadMessageAttachmentsHolder, message, attachment)
                        else -> setupFileView(holder, threadMessageAttachmentsHolder, message, attachment)
                    }

                    threadMessagePlayOutline.beVisibleIf(mimetype.startsWith("video/"))
                }
            } else {
                threadMessageAttachmentsHolder.beGone()
                threadMessagePlayOutline.beGone()
            }
        }
    }

    /**
     * First line of every message: an optional SIM badge (only with multiple SIMs), the sender
     * number (received messages of a group conversation only) and the timestamp, separated by
     * [METADATA_PART_GAP_DP] gaps (1.0.4 / 1.0.5 spec). The "sent" check mark is appended to this
     * line as a compound drawable.
     *
     * The SIM marker used to be literal text of the form `[1]`; it is now a [SimBadgeSpan] drawn
     * like the bottom bar's operator selector (1.0.7 spec).
     */
    private fun buildMetadataText(message: Message): CharSequence {
        val builder = SpannableStringBuilder()
        val gapPx = (METADATA_PART_GAP_DP * activity.resources.displayMetrics.density).roundToInt()

        /** Inserts the [METADATA_PART_GAP_DP] separator, unless nothing has been appended yet. */
        fun appendGap() {
            if (builder.isNotEmpty()) {
                val start = builder.length
                builder.append('\u200B')
                builder.setSpan(
                    GapSpan(gapPx),
                    start,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        fun appendPart(part: String) {
            appendGap()
            builder.append(part)
        }

        if (hasMultipleSIMCards) {
            appendGap()
            val start = builder.length
            builder.append('\u200B')

            val config = activity.config
            val simSize = (config.threadInfoTextSize * activity.resources.displayMetrics.scaledDensity).roundToInt()
            val simIcon = AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.ic_sim_vector)?.mutate()
            val simLabel = simIdMap[message.subscriptionId] ?: "?"
            if (simIcon != null) {
                builder.setSpan(
                    SimBadgeSpan(
                        icon = simIcon,
                        label = simLabel,
                        iconColor = config.threadInfoTextColor,
                        labelColor = config.threadInfoTextColor.getContrastColor(),
                        sizePx = simSize,
                    ),
                    start,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                builder.append(simLabel)
            }
        }
        if (message.isReceivedMessage() && message.senderPhoneNumber.isNotEmpty() && !hideSenderNumber) {
            appendPart(message.senderPhoneNumber)
        }
        appendPart(formatTimestamp(message.millis()))
        return builder
    }

    private fun formatTimestamp(millis: Long): String = DateTime(millis).formatMessageDateTime()

    private fun setupReceivedMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            // Everything (metadata, bubble, and now the attachments inside it) hangs off the
            // wrapper's gravity instead of three separate RelativeLayout rules (1.0.7 spec).
            threadMessageWrapper.gravity = Gravity.START
            applyBubbleMargins(threadMessageWrapper, startDp = 10, endDp = 40)

            // the bubble itself carries the background, so the copyable strings sit inside it
            threadMessageBubble.apply {
                background = bubbleBackground(
                    shapeRes = R.drawable.item_received_background,
                    color = activity.getProperBackgroundColor(),
                    highlighted = message.id == highlightedMessageId,
                )
            }

            threadMessageBody.apply {
                setTextColor(activity.config.threadSnippetTextColor)
                setLinkTextColor(activity.config.linkTextColor)
            }
        }
    }

    private fun setupSentMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            threadMessageWrapper.gravity = Gravity.END
            applyBubbleMargins(threadMessageWrapper, startDp = 40, endDp = 10)

            val primaryColor = activity.getProperPrimaryColor()
            val contrastColor = primaryColor.getContrastColor()

            threadMessageBubble.apply {
                background = bubbleBackground(
                    shapeRes = R.drawable.item_sent_background,
                    color = primaryColor,
                    highlighted = message.id == highlightedMessageId,
                )
            }

            threadMessageBody.apply {
                setTextColor(contrastColor)
                setLinkTextColor(activity.config.linkTextColor)

                if (message.isScheduled) {
                    typeface = Typeface.create(FontHelper.getTypeface(activity), Typeface.ITALIC)
                    val scheduledDrawable = AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.ic_clock_vector)?.apply {
                        applyColorFilter(contrastColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }

                    setCompoundDrawables(null, null, scheduledDrawable, null)
                } else {
                    typeface = FontHelper.getTypeface(activity)
                    setCompoundDrawables(null, null, null, null)
                }
            }
        }
    }

    /**
     * Received messages keep a 10dp gap on their own (left) side and stretch to 40dp from the
     * right edge; sent messages mirror that. Requested by the 1.0.3 spec.
     */
    private fun applyBubbleMargins(view: View, startDp: Int, endDp: Int) {
        val density = activity.resources.displayMetrics.density
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = (startDp * density).toInt()
            marginEnd = (endDp * density).toInt()
        }
    }

    /** Vertical spacing of a message row; the first message uses a smaller top gap. */
    private fun applyHolderTopMargin(view: View, topDp: Int) {
        val topPx = (topDp * activity.resources.displayMetrics.density).roundToInt()
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = topPx
        }
    }

    /**
     * Background of a message bubble: [shapeRes] tinted with [color].
     *
     * When [highlighted] the very same shape is stacked on top once more, tinted with the accent
     * colour at [HIGHLIGHT_ALPHA] - reusing the shape (rather than a plain colour rectangle) is what
     * keeps the highlight inside the bubble's rounded corners and its tail (1.0.9 spec).
     */
    private fun bubbleBackground(shapeRes: Int, color: Int, highlighted: Boolean): Drawable? {
        val base = AppCompatResources.getDrawable(activity, shapeRes)?.mutate() ?: return null
        base.applyColorFilter(color)

        if (!highlighted) {
            return base
        }

        val overlay = AppCompatResources.getDrawable(activity, shapeRes)?.mutate() ?: return base
        overlay.applyColorFilter(activity.config.highlightColor.adjustAlpha(HIGHLIGHT_ALPHA))
        return LayerDrawable(arrayOf(base, overlay))
    }

    private fun setupImageView(holder: ViewHolder, binding: ItemMessageBinding, message: Message, attachment: Attachment) = binding.apply {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()

        val imageView = ItemAttachmentImageBinding.inflate(layoutInflater)
        threadMessageAttachmentsHolder.addView(imageView.root)

        // The attachment now lives inside the bubble, and the bubble is sized by its widest
        // child. Its width budget therefore has to exclude the bubble's own horizontal padding,
        // otherwise a full-width image would push the bubble past the row and the right edge
        // would get clipped (1.0.7 spec).
        val attachmentMaxWidth = (maxChatBubbleWidth - 2 * threadMessageBubble.paddingStart).coerceAtLeast(0)

        val placeholderDrawable = Color.TRANSPARENT.toDrawable()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(FitCenter())

        Glide.with(root.context)
            .load(uri)
            .apply(options)
            .dontAnimate()
            .override(attachmentMaxWidth, attachmentMaxWidth * MAX_MEDIA_HEIGHT_RATIO)
            .downsample(DownsampleStrategy.AT_MOST)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    threadMessagePlayOutline.beGone()
                    threadMessageAttachmentsHolder.removeView(imageView.root)
                    return false
                }

                override fun onResourceReady(dr: Drawable, a: Any, t: Target<Drawable>, d: DataSource, i: Boolean) = false
            })
            .into(imageView.attachmentImage)

        imageView.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
            width = attachmentMaxWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        imageView.attachmentImage.setOnClickListener {
            if (actModeCallback.isSelectable) {
                holder.viewClicked(message)
            } else {
                activity.launchViewIntent(uri, mimetype, attachment.filename)
            }
        }
        imageView.root.setOnLongClickListener {
            holder.viewLongClicked()
            true
        }
    }

    private fun setupAudioView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val audioView = ItemAttachmentAudioBinding.inflate(layoutInflater).apply {
            setupAudio(
                uri = attachment.getUri(),
                onSelect = { holder.viewClicked(message) },
                onLongClick = { holder.viewLongClicked() },
                isSelecting = { actModeCallback.isSelectable }
            )
        }.root
        parent.addView(audioView)
    }

    private fun setupVCardView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        val vCardView = ItemAttachmentVcardBinding.inflate(layoutInflater).apply {
            setupVCardPreview(
                activity = activity,
                uri = uri,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        val intent = Intent(activity, VCardViewerActivity::class.java).also {
                            it.putExtra(EXTRA_VCARD_URI, uri)
                        }
                        activity.startActivity(intent)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        val attachmentView = ItemAttachmentDocumentBinding.inflate(layoutInflater).apply {
            setupDocumentPreview(
                uri = uri,
                title = attachment.filename,
                mimeType = attachment.mimetype,
                onClick = {
                    if (actModeCallback.isSelectable) {
                        holder.viewClicked(message)
                    } else {
                        activity.launchViewIntent(uri, mimetype, attachment.filename)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(attachmentView)
    }

    private fun setupThreadError(view: View) {
        val binding = ItemThreadErrorBinding.bind(view)
        binding.threadError.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize - 4)
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).threadSending.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            setTextColor(textColor)
        }
    }

    inner class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadError -> oldItem.messageId == (newItem as ThreadError).messageId
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadSending -> true
            is ThreadError -> oldItem.messageText == (newItem as ThreadError).messageText
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
        }
    }
}
