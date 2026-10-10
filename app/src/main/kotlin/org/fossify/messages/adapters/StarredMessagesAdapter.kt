package org.fossify.messages.adapters

import android.util.TypedValue
import android.view.Menu
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemStarredMessageBinding
import org.fossify.messages.models.Message

/**
 * Read-only list of starred messages. Selection / contextual action mode is intentionally
 * disabled: tapping a row opens its conversation.
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
    private val fontSize = activity.getTextSize()

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

                val sender = message.senderName.ifEmpty { message.senderPhoneNumber }
                starredTitle.apply {
                    text = sender
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
                    setTextColor(textColor)
                }
                starredBody.apply {
                    text = message.body
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
                    setTextColor(textColor)
                }
                starredDate.apply {
                    text = message.millis().formatDateOrTime(
                        context = activity,
                        hideTimeOnOtherDays = true,
                        showCurrentYear = false
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                    setTextColor(textColor)
                }
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
