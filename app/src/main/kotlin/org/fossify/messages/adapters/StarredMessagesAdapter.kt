package org.fossify.messages.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Menu
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemStarredMessageBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.starMessage
import org.fossify.messages.extensions.unstarMessage
import org.fossify.messages.helpers.AvatarColors
import org.fossify.messages.helpers.extractCopyableStrings
import org.fossify.messages.helpers.extractServiceName
import org.fossify.messages.helpers.formatMessageDateTime
import org.fossify.messages.helpers.sanitizeForDisplay
import org.fossify.messages.helpers.setupCopyableStrings
import org.fossify.messages.models.Message
import org.joda.time.DateTime

/**
 * Renders single messages: it backs the starred tab and, while a search is running, the two
 * conversation tabs as well (a search hit is one message, 1.0.9 spec).
 *
 * Selection / contextual action mode is intentionally disabled: tapping a row opens its
 * conversation.
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

    /**
     * Whether every row shows the star toggle next to its title. Only the search results do - the
     * starred tab holds nothing but starred messages already (1.1.01 spec).
     */
    var showStarToggle: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }
        }

    /**
     * Ids of the starred messages, resolved by the activity on a background thread so binding a row
     * never touches the database (1.1.01 spec).
     */
    var starredMessageIds: Set<Long> = emptySet()

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

                // The conversation rows dim a read snippet to 70% and bold an unread one; this list
                // has to do the same or the snippet colour reads wrong next to the other tabs
                // (1.1.01 spec).
                val style = if (message.read) Typeface.NORMAL else Typeface.BOLD
                val customTypeface = FontHelper.getTypeface(activity)

                starredTitle.apply {
                    text = if (highlight.isEmpty()) displayName else displayName.highlightTextPart(highlight, highlightColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeTitleTextSize.toFloat())
                    setTextColor(config.homeTitleTextColor)
                    setTypeface(customTypeface, style)
                }

                starredBody.apply {
                    val bodyText = sanitizeForDisplay(message.body)
                    text = if (highlight.isEmpty()) bodyText else bodyText.highlightTextPart(highlight, highlightColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeSnippetTextSize.toFloat())
                    setTextColor(config.homeSnippetTextColor)
                    alpha = if (message.read) READ_SNIPPET_ALPHA else 1f
                    setTypeface(customTypeface, style)
                }

                starredDate.apply {
                    text = DateTime(message.millis()).formatMessageDateTime()
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, config.homeInfoTextSize.toFloat())
                    setTextColor(config.homeInfoTextColor)
                    setTypeface(customTypeface, style)
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

                // Star toggle: solid amber while starred, hollow with the generic border colour
                // while not (1.1.01 spec).
                starredStar.beVisibleIf(showStarToggle)
                if (showStarToggle) {
                    val isStarred = starredMessageIds.contains(message.id)
                    starredStar.apply {
                        setImageResource(
                            if (isStarred) R.drawable.ic_star_filled_vector
                            else R.drawable.ic_star_outline_vector
                        )
                        imageTintList = ColorStateList.valueOf(
                            if (isStarred) STAR_FILLED_COLOR
                            else activity.getColor(org.fossify.commons.R.color.divider_grey)
                        )
                        setOnClickListener {
                            toggleStar(message, holder.bindingAdapterPosition)
                        }
                    }
                } else {
                    starredStar.setOnClickListener(null)
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
                    fontSizeSp = config.copyableTextSize.toFloat()
                )
            }
        }
        bindViewHolder(holder)
    }

    /**
     * Stars / unstars one message straight from the row and flips just that row's icon. Tapping the
     * star must not open the conversation, which is why the icon carries its own click listener.
     */
    private fun toggleStar(message: Message, position: Int) {
        val isStarred = starredMessageIds.contains(message.id)
        if (isStarred) {
            activity.unstarMessage(message.id)
            starredMessageIds = starredMessageIds - message.id
        } else {
            activity.starMessage(message.id)
            starredMessageIds = starredMessageIds + message.id
        }

        if (position >= 0) {
            notifyItemChanged(position)
        }
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

    companion object {
        /** Read snippets are dimmed the same way the conversation rows do it (1.0.4 spec). */
        private const val READ_SNIPPET_ALPHA = 0.7f

        /** Solid star colour of a starred search result (1.1.01 spec). */
        private const val STAR_FILLED_COLOR = 0xFFFFD600.toInt()
    }
}
