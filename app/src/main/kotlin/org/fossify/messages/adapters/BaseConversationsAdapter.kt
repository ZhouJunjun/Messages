package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemConversationBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getAllDrafts
import org.fossify.messages.helpers.AvatarColors
import org.fossify.messages.helpers.extractCopyableStrings
import org.fossify.messages.helpers.formatMessageDateTime
import org.fossify.messages.helpers.sanitizeForDisplay
import org.fossify.messages.helpers.serviceDisplayName
import org.fossify.messages.helpers.setupCopyableStrings
import org.fossify.messages.helpers.stripRedundantSignature
import org.fossify.messages.models.Conversation
import org.joda.time.DateTime

@Suppress("LeakingThis")
abstract class BaseConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<Conversation>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ConversationDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
),
    RecyclerViewFastScroller.OnPopupTextUpdate {
    private var fontSize = activity.getTextSize()
    private var drafts = HashMap<Long, String>()

    private var recyclerViewState: Parcelable? = null

    /**
     * Term highlighted inside the title / snippet while a search is active. Setting it rebinds the
     * visible rows, otherwise typing one more character would keep the old highlight whenever the
     * matching set happens to stay the same (1.0.7 spec).
     */
    var highlightText: String = ""
        set(value) {
            if (field != value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }
        }

    init {
        setupDragListener(true)
        setHasStableIds(true)
        updateDrafts()

        // The scroll position is saved every time the list settles, so a restore below always
        // lands on where the user actually stopped rather than where the last submitList began
        // (1.1.04 fix).
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    saveRecyclerViewState()
                }
            }
        })

        // Restores used to run on `onChanged` too, i.e. on every plain `notifyDataSetChanged` -
        // and `MainActivity.refreshListAppearance()` fires one on every onResume. That pushed
        // the list back to the position saved at the last `submitList`, so returning from a
        // conversation / settings jumped the list (1.1.04 fix, review B1). Restores now happen
        // only for the structural updates the diff actually reports; an appearance-only repaint
        // keeps whatever position is on screen.
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
                restoreRecyclerViewState()

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
                restoreRecyclerViewState()
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateFontSize() {
        fontSize = activity.getTextSize()
        notifyDataSetChanged()
    }

    fun updateConversations(
        newConversations: ArrayList<Conversation>,
        commitCallback: (() -> Unit)? = null,
    ) {
        saveRecyclerViewState()
        // submitted as-is (no defensive copy) on purpose: ListAdapter.submitList returns
        // immediately for an identical list reference, which is what makes swiping between tabs
        // free once the per-tab cache hands back the same instance (1.0.8 spec)
        submitList(newConversations, commitCallback)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDrafts() {
        ensureBackgroundThread {
            val newDrafts = HashMap<Long, String>()
            fetchDrafts(newDrafts)
            activity.runOnUiThread {
                if (drafts.hashCode() != newDrafts.hashCode()) {
                    drafts = newDrafts
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun getSelectableItemCount() = itemCount

    protected fun getSelectedItems() = currentList.filter {
        selectedKeys.contains(it.hashCode())
    } as ArrayList<Conversation>

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bindView(
            conversation,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(itemView, conversation)
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int) = getItem(position).threadId

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val itemView = ItemConversationBinding.bind(holder.itemView)
            Glide.with(activity).clear(itemView.conversationImage)
        }
    }

    private fun fetchDrafts(drafts: HashMap<Long, String>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        ItemConversationBinding.bind(view).apply {
            root.setupViewBackground(activity)
            val smsDraft = drafts[conversation.threadId]
            draftIndicator.beVisibleIf(!smsDraft.isNullOrEmpty())
            draftIndicator.setTextColor(properPrimaryColor)

            pinIndicator.beVisibleIf(
                activity.config.pinnedConversations.contains(conversation.threadId.toString())
            )
            pinIndicator.applyColorFilter(textColor)

            conversationFrame.isSelected = selectedKeys.contains(conversation.hashCode())

            val config = activity.config
            // The `【签名】` is only a title candidate for senders whose number is actually
            // service-like (1.1.04 fix): a private message reading "【急】晚上聚餐" used to
            // introduce its row as "急" even though the conversation no longer classifies as a
            // service one.
            // 1.1.06 / 1.1.08 fix: the signature has to come from the newest *received* message,
            // never from the newest message overall - once we reply inside a service thread the
            // snippet is our own text and the row fell back to the bare number. That body is
            // `serviceSnippet`, persisted by the provider scan; the helper owns the whole rule so
            // this row and the thread toolbar always agree.
            val serviceName = conversation.serviceDisplayName()

            // The resolved contact name wins over the `【签名】` extracted from the message body, so
            // a row reads exactly like its thread toolbar does - e.g. "中国联通" instead of
            // "积分提醒". Only when the number has no contact name do we show the signature, and
            // failing that the raw number (1.0.7 spec).
            val displayName = conversation.title.takeIf { AvatarColors.isRealName(it) }
                ?: serviceName
                ?: conversation.title

            val highlight = highlightText
            val highlightColor = config.highlightColor

            conversationAddress.apply {
                text = if (highlight.isEmpty()) displayName else displayName.highlightTextPart(highlight, highlightColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeTitleTextSize.toFloat())
                setTextColor(config.homeTitleTextColor)
            }

            conversationBodyShort.apply {
                // A leading `【签名】` that just repeats this row's own title is dropped, so the
                // snippet starts at the real body text (1.1.07 spec).
                val snippetText = stripRedundantSignature(
                    sanitizeForDisplay(smsDraft ?: conversation.snippet),
                    displayName
                )
                text = if (highlight.isEmpty()) snippetText else snippetText.highlightTextPart(highlight, highlightColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeSnippetTextSize.toFloat())
                setTextColor(config.homeSnippetTextColor)
            }

            conversationDate.apply {
                text = DateTime(conversation.date * 1000L).formatMessageDateTime()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeInfoTextSize.toFloat())
                setTextColor(config.homeInfoTextColor)
            }

            val isUnread = !conversation.read
            val style = if (isUnread) {
                conversationBodyShort.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                conversationBodyShort.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }
            val customTypeface = FontHelper.getTypeface(activity)
            conversationAddress.setTypeface(customTypeface, style)
            conversationBodyShort.setTypeface(customTypeface, style)
            conversationDate.setTypeface(customTypeface, style)

            setupBadgeCount(unreadCountBadge, isUnread, conversation.unreadCount)

            // A colored avatar showing the first up-to-4 characters of the same display name the
            // title uses. Anything without a real name (unknown numbers, services without a
            // signature) falls back to the neutral placeholder avatar.
            val avatarName = displayName.takeIf { AvatarColors.isRealName(it) }
            if (avatarName != null) {
                conversationImage.beGone()
                conversationAvatarText.apply {
                    beVisible()
                    text = AvatarColors.textFor(avatarName)
                    backgroundTintList = ColorStateList.valueOf(AvatarColors.colorFor(avatarName))
                    setTextColor(Color.WHITE)
                }
            } else {
                conversationAvatarText.beGone()
                conversationImage.apply {
                    beVisible()
                    setImageResource(R.drawable.ic_default_avatar)
                }
            }

            val copyables = extractCopyableStrings(conversation.snippet)
            // Dividers use the same colour as the thread toolbar's bottom border (1.0.5 spec).
            val dividerColor = activity.getColor(org.fossify.commons.R.color.divider_grey)
            conversationCopyablesDivider.apply {
                beVisibleIf(copyables.isNotEmpty())
                setBackgroundColor(dividerColor)
            }
            conversationCopyables.setupCopyableStrings(
                context = activity,
                items = copyables,
                textColor = config.copyableTextColor,
                fontSizeSp = config.copyableTextSize.toFloat()
            )
        }
    }

    private fun setupBadgeCount(view: TextView, isUnread: Boolean, count: Int) {
        view.apply {
            beVisibleIf(isUnread)
            if (isUnread) {
                text = when {
                    count > MAX_UNREAD_BADGE_COUNT -> "$MAX_UNREAD_BADGE_COUNT+"
                    count == 0 -> ""
                    else -> count.toString()
                }
                setTextColor(properPrimaryColor.getContrastColor())
                background?.applyColorFilter(properPrimaryColor)
            }
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""

    private fun saveRecyclerViewState() {
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    private fun restoreRecyclerViewState() {
        recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areContentsTheSame(oldItem, newItem)
        }
    }

    companion object {
        private const val MAX_UNREAD_BADGE_COUNT = 99
    }
}
