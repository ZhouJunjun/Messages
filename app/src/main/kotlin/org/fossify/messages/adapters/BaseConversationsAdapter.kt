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
import org.fossify.messages.helpers.extractServiceName
import org.fossify.messages.helpers.formatMessageDateTime
import org.fossify.messages.helpers.sanitizeForDisplay
import org.fossify.messages.helpers.setupCopyableStrings
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

    init {
        setupDragListener(true)
        setHasStableIds(true)
        updateDrafts()

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = restoreRecyclerViewState()
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
        submitList(newConversations.toList(), commitCallback)
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
            val serviceName = extractServiceName(conversation.snippet)

            conversationAddress.apply {
                text = serviceName ?: conversation.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeTitleTextSize.toFloat())
                setTextColor(config.homeTitleTextColor)
            }

            conversationBodyShort.apply {
                text = sanitizeForDisplay(smsDraft ?: conversation.snippet)
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

            // A colored avatar showing the first up-to-4 characters of the display name - the
            // service name, or the contact name. Anything without a real name (unknown numbers,
            // services without a signature) falls back to the neutral placeholder avatar.
            val avatarName = serviceName ?: conversation.title.takeIf { AvatarColors.isRealName(it) }
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
            // Dividers follow the snippet colour at 80% alpha (1.0.4 spec).
            val dividerColor = config.homeSnippetTextColor.adjustAlpha(0.8f)
            conversationCopyablesDivider.apply {
                beVisibleIf(copyables.isNotEmpty())
                setBackgroundColor(dividerColor)
            }
            conversationCopyables.setupCopyableStrings(
                context = activity,
                items = copyables,
                textColor = config.copyableTextColor,
                dividerColor = dividerColor,
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
