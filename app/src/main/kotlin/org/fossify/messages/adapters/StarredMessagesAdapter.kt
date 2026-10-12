package org.fossify.messages.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Menu
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.DiffUtil
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
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
import org.fossify.messages.helpers.isServiceNumber
import org.fossify.messages.helpers.sanitizeForDisplay
import org.fossify.messages.helpers.setupCopyableStrings
import org.fossify.messages.helpers.stripRedundantSignature
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
 * per the 1.0.3 spec. Every row carries the star toggle to the left of its title: on a search
 * result it stars / unstars the hit, on the starred tab it unstars it and the row leaves the list
 * (1.1.06 spec).
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
     * Whether an unstarred row drops out of the list. True for the starred tab - a row there is a
     * starred message and nothing else, so unstarring it has to remove it, not just grey the icon out
     * (1.1.06 spec). False for search results, where a hit stays visible after being unstarred.
     */
    var removeUnstarredFromList: Boolean = false

    /**
     * Ids of the starred messages, resolved by the activity on a background thread so binding a row
     * never touches the database (1.1.01 spec).
     */
    var starredMessageIds: Set<Long> = emptySet()

    /**
     * Notifies the activity of every star toggle so the *activity's* id set stays the single
     * source of truth. Without this, the next `refreshPage` pushed the activity's stale set back
     * into this adapter and the row's star icon reverted even though the database was updated
     * (1.1.04 fix, review A1).
     */
    var onStarToggledListener: ((messageId: Long, starred: Boolean) -> Unit)? = null

    /**
     * The icon font that draws the star glyph, parsed once and reused: `createFromAsset()` reloads
     * the file on every call and rows are re-bound while scrolling (1.1.02 second rework).
     */
    private val starTypeface: Typeface by lazy {
        Typeface.createFromAsset(activity.assets, STAR_FONT_ASSET)
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
                // The `【签名】` is only a title candidate for senders whose number is actually
                // service-like: with the 1.1.04 classifier a private message reading "【急】晚上
                // 聚餐" no longer files its thread under the service tab, so its row must not
                // introduce itself as "急" either.
                val serviceName = extractServiceName(message.body)
                    ?.takeIf { isServiceNumber(message.senderPhoneNumber) }

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
                    // A leading `【签名】` that just repeats this row's own title is dropped, so the
                    // snippet starts at the real body text (1.1.07 spec).
                    val bodyText = stripRedundantSignature(
                        sanitizeForDisplay(message.body),
                        displayName
                    )
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

                // Star toggle: one and the same "⭐" glyph in both states - amber while starred,
                // the app's default border colour while not (1.1.02 second rework). It is drawn by
                // the bundled icon font instead of a drawable, so there is no box to measure and no
                // ratio to apply: the glyph simply follows the title's font size, and the flag is
                // colour-only again. Since 1.1.06 it sits to the LEFT of the title and shows on every
                // row of this adapter (search results and the starred tab alike).
                val isStarred = starredMessageIds.contains(message.id)
                starredStar.apply {
                    // 🔴 U+2B50 is an emoji code point, and emoji2 is part of the app (commons
                    // pulls it in and the merged manifest registers
                    // androidx.emoji2.text.EmojiCompatInitializer). Left alone, emoji2 makes
                    // this TextView draw the glyph with the *system* emoji font - a coloured
                    // star that ignores both our typeface and our tint. setEmojiCompatEnabled
                    // (false) is the supported opt-out: it detaches the emoji transformation
                    // method / text watcher, so the bundled font draws the glyph. The layout
                    // carries the same flag via app:emojiCompatEnabled="false", applied at
                    // inflation; this call is the belt to its braces for recycled rows.
                    (this as? AppCompatTextView)?.setEmojiCompatEnabled(false)
                    text = STAR_GLYPH
                    typeface = starTypeface
                    // Two font-size steps below the title, per the 1.1.03 spec ("title 字号减 2").
                    // A glyph has no box of its own, so it just inherits this size - nothing here
                    // can drift from row to row.
                    setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        (config.homeTitleTextSize - STAR_TO_TITLE_SIZE_STEP).toFloat()
                    )
                    setTextColor(
                        if (isStarred) STAR_FILLED_COLOR
                        else activity.getColor(org.fossify.commons.R.color.divider_grey)
                    )
                    setOnClickListener {
                        toggleStar(message, holder.bindingAdapterPosition)
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
                    fontSizeSp = config.copyableTextSize.toFloat()
                )
            }
        }
        bindViewHolder(holder)
    }

    /**
     * Stars / unstars one message straight from the row and flips just that row's icon. Tapping the
     * star must not open the conversation, which is why the icon carries its own click listener.
     *
     * On the starred tab an unstarred row is dropped from the list rather than dimmed
     * ([removeUnstarredFromList]); the filtered re-submit keeps the diff - and with it the row
     * animations and stable ids - doing the removal.
     *
     * 🔴 The database write MUST leave the main thread: `starred_messages` is a plain Room
     * database without `allowMainThreadQueries()`, so calling it from the click listener throws
     * `IllegalStateException: Cannot access database on the main thread ...` (the toast the user
     * reported). The in-memory id set is flipped synchronously so the icon still updates instantly.
     */
    private fun toggleStar(message: Message, position: Int) {
        val isStarred = starredMessageIds.contains(message.id)
        starredMessageIds = if (isStarred) {
            starredMessageIds - message.id
        } else {
            starredMessageIds + message.id
        }
        onStarToggledListener?.invoke(message.id, !isStarred)

        if (isStarred && removeUnstarredFromList) {
            submitList(currentList.filterNot { it.id == message.id })
        } else if (position >= 0) {
            notifyItemChanged(position)
        }

        ensureBackgroundThread {
            if (isStarred) {
                activity.unstarMessage(message.id)
            } else {
                activity.starMessage(message.id)
            }
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

        /** Star colour of a starred search result (1.1.01 spec); unstarred uses the border grey. */
        private const val STAR_FILLED_COLOR = 0xFFFFD600.toInt()

        /** Icon font in `app/src/main/assets/`, holding the single U+2B50 star glyph (1.1.02 rework). */
        private const val STAR_FONT_ASSET = "fonts/MyTree-Regular.ttf"

        /** The glyph the font above is built for - a text star, tinted per state rather than painted. */
        private const val STAR_GLYPH = "\u2B50"

        /** 1.1.03 spec: the star sits two font-size steps below the title. */
        private const val STAR_TO_TITLE_SIZE_STEP = 2
    }
}
