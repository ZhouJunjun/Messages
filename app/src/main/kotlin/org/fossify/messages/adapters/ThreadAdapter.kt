package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
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
import org.fossify.messages.extensions.isMessageStarred
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
import org.fossify.messages.helpers.THREAD_RECEIVED_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_ERROR
import org.fossify.messages.helpers.THREAD_SENT_MESSAGE_SENDING
import org.fossify.messages.helpers.extractCopyableStrings
import org.fossify.messages.helpers.formatMessageDateTime
import org.fossify.messages.helpers.generateStableId
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
    var starredIds: Set<Long> = emptySet()

    /** subscriptionId -> displayed SIM slot number ("1", "2", ...), filled by the activity. */
    var simIdMap: Map<Int, String> = emptyMap()

    /**
     * messageId -> delivered flag for the sent messages that reached the "sent" state. Filled by
     * the activity; the check mark is rendered inside the message's metadata line (1.0.4 spec).
     */
    var sentStatus: Map<Long, Boolean> = emptyMap()

    @SuppressLint("MissingPermission")
    private val hasMultipleSIMCards = (activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0) > 1
    private val maxChatBubbleWidth = (activity.usableScreenSize.x * 0.8f).toInt()

    companion object {
        private const val MAX_MEDIA_HEIGHT_RATIO = 3

        /** Gap between the SIM label, the sender number and the timestamp in the metadata line. */
        private const val METADATA_PART_GAP_DP = 30
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
        ensureBackgroundThread {
            if (activity.isMessageStarred(message.id)) {
                activity.unstarMessage(message.id)
                starredIds = starredIds - message.id
            } else {
                activity.starMessage(message.id)
                starredIds = starredIds + message.id
            }
            activity.runOnUiThread {
                finishActMode()
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

    fun updateMessages(
        newMessages: List<ThreadItem>,
        scrollPosition: Int = -1,
        smoothScroll: Boolean = false
    ) {
        val latestMessages = newMessages.toMutableList()
        submitList(latestMessages) {
            if (scrollPosition != -1) {
                if (smoothScroll) {
                    recyclerView.smoothScrollToPosition(scrollPosition)
                } else {
                    recyclerView.scrollToPosition(scrollPosition)
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

                // sent messages carry the "sent" check mark at the end of the first line,
                // tinted with the copyable-string colour (1.0.4 spec)
                val delivered = if (received) null else sentStatus[message.id]
                val checkDrawable = delivered?.let {
                    AppCompatResources.getDrawable(
                        activity,
                        if (it) R.drawable.ic_check_double_vector else org.fossify.commons.R.drawable.ic_check_vector
                    )?.mutate()?.apply {
                        applyColorFilter(config.copyableTextColor)
                        val size = lineHeight
                        setBounds(0, 0, size, size)
                    }
                }
                setCompoundDrawablesRelative(null, null, checkDrawable, null)
            }

            threadMessageBody.apply {
                text = message.body
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
            // Dividers follow the snippet colour at 80% alpha (1.0.4 spec).
            val dividerColor = config.threadSnippetTextColor.adjustAlpha(0.8f)
            threadMessageCopyablesDivider.apply {
                beVisibleIf(copyables.isNotEmpty())
                setBackgroundColor(dividerColor)
            }
            threadMessageCopyables.setupCopyableStrings(
                context = activity,
                items = copyables,
                textColor = config.copyableTextColor,
                dividerColor = dividerColor,
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
     * First line of every message: an optional SIM label (only with multiple SIMs), the sender
     * number (received messages only) and the timestamp, separated by 30dp gaps (1.0.4 spec).
     * The "sent" check mark is appended to this line as a compound drawable.
     */
    private fun buildMetadataText(message: Message): CharSequence {
        val builder = SpannableStringBuilder()
        val gapPx = (METADATA_PART_GAP_DP * activity.resources.displayMetrics.density).roundToInt()

        fun appendPart(part: String) {
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
            builder.append(part)
        }

        if (hasMultipleSIMCards) {
            appendPart("[${simIdMap[message.subscriptionId] ?: "?"}]")
        }
        if (message.isReceivedMessage() && message.senderPhoneNumber.isNotEmpty()) {
            appendPart(message.senderPhoneNumber)
        }
        appendPart(formatTimestamp(message.millis()))
        return builder
    }

    private fun formatTimestamp(millis: Long): String = DateTime(millis).formatMessageDateTime()

    private fun setupReceivedMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            alignStart(threadMessageMetadata)
            alignStart(threadMessageAttachmentsHolder)
            alignStart(threadMessageBubble)
            applyBubbleMargins(threadMessageWrapper, startDp = 10, endDp = 40)

            // the bubble itself carries the background, so the copyable strings sit inside it
            threadMessageBubble.apply {
                background = AppCompatResources.getDrawable(activity, R.drawable.item_received_background)
                background?.applyColorFilter(activity.getProperBackgroundColor())
            }

            threadMessageBody.apply {
                setTextColor(activity.config.threadSnippetTextColor)
                setLinkTextColor(activity.config.linkTextColor)
            }
        }
    }

    private fun setupSentMessageView(messageBinding: ItemMessageBinding, message: Message) {
        messageBinding.apply {
            alignEnd(threadMessageMetadata)
            alignEnd(threadMessageAttachmentsHolder)
            alignEnd(threadMessageBubble)
            applyBubbleMargins(threadMessageWrapper, startDp = 40, endDp = 10)

            val primaryColor = activity.getProperPrimaryColor()
            val contrastColor = primaryColor.getContrastColor()

            threadMessageBubble.apply {
                background = AppCompatResources.getDrawable(activity, R.drawable.item_sent_background)
                background?.applyColorFilter(primaryColor)
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

    private fun alignStart(view: View) {
        view.updateLayoutParams<RelativeLayout.LayoutParams> {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            removeRule(RelativeLayout.ALIGN_PARENT_END)
        }
    }

    private fun alignEnd(view: View) {
        view.updateLayoutParams<RelativeLayout.LayoutParams> {
            addRule(RelativeLayout.ALIGN_PARENT_END)
            removeRule(RelativeLayout.ALIGN_PARENT_START)
        }
    }

    private fun setupImageView(holder: ViewHolder, binding: ItemMessageBinding, message: Message, attachment: Attachment) = binding.apply {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()

        val imageView = ItemAttachmentImageBinding.inflate(layoutInflater)
        threadMessageAttachmentsHolder.addView(imageView.root)

        val placeholderDrawable = Color.TRANSPARENT.toDrawable()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(FitCenter())

        Glide.with(root.context)
            .load(uri)
            .apply(options)
            .dontAnimate()
            .override(maxChatBubbleWidth, maxChatBubbleWidth * MAX_MEDIA_HEIGHT_RATIO)
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
            width = maxChatBubbleWidth
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
