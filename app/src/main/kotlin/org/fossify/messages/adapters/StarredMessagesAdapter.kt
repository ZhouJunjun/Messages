package org.fossify.messages.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Menu
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemStarredMessageBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.AvatarColors
import org.fossify.messages.helpers.extractCopyableStrings
import org.fossify.messages.helpers.extractServiceName
import org.fossify.messages.helpers.formatMessageDateTime
import org.fossify.messages.helpers.sanitizeForDisplay
import org.fossify.messages.helpers.setupCopyableStrings
import org.fossify.messages.models.Message
import org.joda.time.DateTime

/**
 * Read-only list of starred messages. Selection / contextual action mode is intentionally
 * disabled: tapping a row opens its conversation.
 *
 * The row is laid out exactly like the conversation lists (coloured avatar, title, two-line
 * snippet, date and the copyable-string row) and follows the same custom-appearance settings,
 * per the 1.0.3 spec.
 */
class StarredMessagesAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<Message>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = StarredMessageDiffCallback(),
    itemClick = itemClick
) {
    /**
     * Term highlighted inside the title / body while a search is active. Setting it rebinds the
     * visible rows so an unchanged match set still picks up the new highlight (1.0.7 spec).
     */
    var highlightText: String = ""
        set(value) {
            if (field != value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }
        }

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = 0

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStarredMessageBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)
        holder.bindView(message, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            ItemStarredMessageBinding.bind(itemView).apply {
                root.setupViewBackground(activity)

                val config = activity.config
                val sender = message.senderName.ifEmpty { message.senderPhoneNumber }
                val serviceName = extractServiceName(message.body)

                // Same precedence as the conversation lists: contact name, then the `【签名】`,
                // then the raw number, so all three tabs name a sender identically (1.0.7 spec).
                val displayName = sender.takeIf { AvatarColors.isRealName(it) }
                    ?: serviceName
                    ?: sender

                val highlight = highlightText
                val highlightColor = config.highlightColor

                starredTitle.apply {
                    text = if (highlight.isEmpty()) displayName else displayName.highlightTextPart(highlight, highlightColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeTitleTextSize.toFloat())
                    setTextColor(config.homeTitleTextColor)
                }

                starredBody.apply {
                    val bodyText = sanitizeForDisplay(message.body)
                    text = if (highlight.isEmpty()) bodyText else bodyText.highlightTextPart(highlight, highlightColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeSnippetTextSize.toFloat())
                    setTextColor(config.homeSnippetTextColor)
                }

                starredDate.apply {
                    text = DateTime(message.millis()).formatMessageDateTime()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeInfoTextSize.toFloat())
                    setTextColor(config.homeInfoTextColor)
                }

                val avatarName = displayName.takeIf { AvatarColors.isRealName(it) }
                if (avatarName != null) {
                    starredAvatarImage.beGone()
                    starredAvatarText.apply {
                        beVisible()
                        text = AvatarColors.textFor(avatarName)
                        backgroundTintList = ColorStateList.valueOf(AvatarColors.colorFor(avatarName))
                        setTextColor(Color.WHITE)
                    }
                } else {
                    starredAvatarText.beGone()
                    starredAvatarImage.apply {
                        beVisible()
                        setImageResource(R.drawable.ic_default_avatar)
                    }
                }

                val copyables = extractCopyableStrings(message.body)
                // Dividers use the same colour as the thread toolbar's bottom border (1.0.5 spec).
                val dividerColor = activity.getColor(org.fossify.commons.R.color.divider_grey)
                starredCopyablesDivider.apply {
                    beVisibleIf(copyables.isNotEmpty())
                    setBackgroundColor(dividerColor)
                }
                starredCopyables.setupCopyableStrings(
                    context = activity,
                    items = copyables,
                    textColor = config.copyableTextColor,
                    dividerColor = dividerColor,
                    fontSizeSp = config.copyableTextSize.toFloat()
                )
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int) = getItem(position).id

    private class StarredMessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id &&
                oldItem.body == newItem.body &&
                oldItem.read == newItem.read
        }
    }
}
