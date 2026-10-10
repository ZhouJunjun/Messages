package org.fossify.messages.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Menu
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemStarredMessageBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.AvatarColors
import org.fossify.messages.helpers.extractCopyableStrings
import org.fossify.messages.helpers.extractServiceName
import org.fossify.messages.helpers.sanitizeForDisplay
import org.fossify.messages.helpers.setupCopyableStrings
import org.fossify.messages.models.Message
import org.joda.time.DateTime
import org.joda.time.LocalDate

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

                starredTitle.apply {
                    text = serviceName ?: sender
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeTitleTextSize.toFloat())
                    setTextColor(config.homeTitleTextColor)
                }

                starredBody.apply {
                    text = sanitizeForDisplay(message.body)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeSnippetTextSize.toFloat())
                    setTextColor(config.homeSnippetTextColor)
                }

                starredDate.apply {
                    val dateTime = DateTime(message.millis())
                    val isToday = dateTime.toLocalDate() == LocalDate.now()
                    text = dateTime.toString(if (isToday) "HH:mm:ss" else "yy/MM/dd HH:mm")
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeInfoTextSize.toFloat())
                    setTextColor(config.homeInfoTextColor)
                }

                val avatarName = serviceName ?: sender.takeIf { AvatarColors.isRealName(it) }
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
                starredCopyablesDivider.apply {
                    beVisibleIf(copyables.isNotEmpty())
                    setBackgroundColor(config.homeInfoTextColor)
                }
                starredCopyables.setupCopyableStrings(
                    context = activity,
                    items = copyables,
                    textColor = config.copyableTextColor,
                    dividerColor = config.homeInfoTextColor,
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
