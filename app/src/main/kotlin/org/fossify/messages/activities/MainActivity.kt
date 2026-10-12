package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.StarredMessagesAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getStarredMessageIds
import org.fossify.messages.extensions.getStarredMessages
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.scanConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.searchMessages
import org.fossify.messages.extensions.updateLastConversationMessage
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.AvatarColors
import org.fossify.messages.helpers.isServiceConversation
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

private const val TAB_CONVERSATIONS = 0
private const val TAB_SERVICES = 1
private const val TAB_STARRED = 2

/** One pager page per tab; ViewPager2 hosts all three at once (1.0.8 rework). */
private const val TAB_COUNT = 3

/** Search box / hint text; commons' `big_text_size` (18sp) minus 2 (1.0.7) minus 1 (1.0.8). */
private const val SEARCH_TEXT_SIZE_SP = 15f

/**
 * Search box geometry.
 *
 * commons' `menu_search.xml` pins the box to `?attr/actionBarSize` (56dp) with 16dp insets on every
 * side and lets the EditText keep its own minHeight, which is why the box kept looking bulky no
 * matter what the runtime colours did. Both the height and the insets are cut down hard here and
 * the EditText's minHeight is cleared, so it can no longer stretch the box back open (1.0.8 rework).
 */
private const val SEARCH_BOX_HEIGHT_DP = 36f
private const val SEARCH_BOX_H_PADDING_DP = 6f

/** Debounce applied to search-box input before the query runs (1.0.8 spec: 1s). */
private const val SEARCH_DEBOUNCE_MS = 1000L

/** Elevation of the tab row's overflow popup; the spec asks for a bigger shadow (1.0.8). */
private const val POPUP_MENU_ELEVATION_DP = 12f

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true
    
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null

    private var currentTab = TAB_CONVERSATIONS
    private var allConversationsForTabs = ArrayList<Conversation>()

    /**
     * Search state.
     *
     * As of 1.0.9 a search result is one *message*, not one conversation: every hit from the
     * telephony provider becomes its own row, drawn with the very same row layout the conversation
     * lists use, and the tab strip keeps splitting the hits (conversations / notifications /
     * starred). Until 1.0.8 a hit was a whole conversation with the matching message promoted to
     * its snippet, which is why a thread with three matching messages only ever showed up once.
     */
    private var searchActive = false
    private var searchMessageResults: List<Message> = emptyList()

    /**
     * Ids of every starred message, refreshed on a background thread whenever a search runs, so a
     * search row can draw its star without touching the database while binding (1.1.01 spec).
     */
    private var starredMessageIds: Set<Long> = emptySet()

    /** Debounce plumbing for the search box (1.0.8 spec: 1s). */
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    /**
     * What each tab currently shows, keyed by `tab|query`.
     *
     * Switching tabs used to re-filter and re-sort every conversation on the UI thread, and the
     * pinned-conversation lookup inside the comparator read SharedPreferences once per comparison;
     * together that is what made left/right swiping stutter (1.0.8 spec). With this cache a swipe
     * between two tabs re-submits the very same list instance, and `submitList` short-circuits on
     * an identical reference.
     */
    private val conversationCache = HashMap<String, CachedConversationList>()

    private class CachedConversationList(
        val conversations: ArrayList<Conversation>,
        val empty: Boolean,
        val sourceEmpty: Boolean,
    )

    private var tabConversationsView: TextView? = null
    private var tabServicesView: TextView? = null
    private var tabStarredView: TextView? = null
    private var tabRowMenuView: ImageView? = null

    /**
     * One list per tab, owned by the ViewPager2 pages. Holding a reference to each page's
     * RecyclerView is what lets a data refresh reach all three tabs, so the neighbour a drag
     * reveals already holds its rows (1.0.8 rework).
     */
    private val pageLists = arrayOfNulls<MyRecyclerView>(TAB_COUNT)
    private val pageScrollers = arrayOfNulls<RecyclerViewFastScroller>(TAB_COUNT)

    private lateinit var pagerAdapter: TabPagerAdapter

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        setupSearchBox()
        setupTabPager()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsPager))

        clearAllMessagesIfNeeded {
            loadMessages()
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()

        pageLists.forEach { list ->
            when (val adapter = list?.adapter) {
                is ConversationsAdapter -> {
                    if (storedTextColor != getProperTextColor()) {
                        adapter.updateTextColor(getProperTextColor())
                    }

                    if (storedFontSize != config.fontSize) {
                        adapter.updateFontSize()
                    }

                    adapter.updateDrafts()
                }

                is StarredMessagesAdapter -> {
                    if (storedTextColor != getProperTextColor()) {
                        adapter.updateTextColor(getProperTextColor())
                    }
                }
            }
        }

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())
        updateTabColors()

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        pageScrollers.forEach { it?.updateColors(properPrimaryColor) }
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
        refreshListAppearance()
        restoreAvatarTextColors()

        // Leaving for the settings screen and coming back can recreate this activity (a custom
        // appearance or theme change does), which resets the in-memory search state while the
        // search box keeps its text - the results then silently fell back to the full list
        // (1.1.01 bug fix). Re-running the search whenever the box holds a query that is not the
        // one currently being shown puts the results back.
        val searchBoxText = binding.mainMenu.binding.topToolbarSearch.text?.toString().orEmpty()
        if (searchBoxText.isNotEmpty() && (searchBoxText != lastSearchedText || !searchActive)) {
            runSearch(searchBoxText)
        } else if (searchActive) {
            // The box text and the shown query match, so there is nothing to re-run (review M4) -
            // but messages may have been starred from a thread screen while this activity sat
            // behind it. Re-read the id set once so the search rows' stars draw the fresh state
            // (1.1.04 fix); the toggle listener keeps it live for toggles made right here.
            ensureBackgroundThread {
                val ids = try {
                    getStarredMessageIds()
                } catch (_: Exception) {
                    return@ensureBackgroundThread
                }
                runOnUiThread { starredMessageIds = ids }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            appLockManager.lock()
            false
        }
    }

    /**
     * Wires up the ViewPager2 that hosts the three tab pages.
     *
     * `offscreenPageLimit` is raised to the page count on purpose: all three pages then exist at
     * the same time, so a drag reveals the neighbour's real list instead of a blank page and
     * nothing has to inflate a RecyclerView in the middle of the gesture. That, together with the
     * pager's own page-slide animation, is the "page switch" effect the 1.0.8 rework asks for.
     */
    private fun setupTabPager() {
        pagerAdapter = TabPagerAdapter()
        binding.conversationsPager.apply {
            adapter = pagerAdapter
            offscreenPageLimit = TAB_COUNT
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    onTabPageSelected(position)
                }
            })
        }
    }

    /**
     * Keeps the tab strip and the surrounding chrome in sync while the pager moves, whether the
     * switch came from tapping a tab (which just calls `setCurrentItem`) or from a drag.
     */
    private fun onTabPageSelected(position: Int) {
        if (currentTab == position) {
            return
        }

        currentTab = position
        updateTabColors()
        binding.conversationsFab.beVisibleIf(position != TAB_STARRED)
        binding.conversationsProgressBar.beGone()
        binding.noConversationsPlaceholder.beGone()
        binding.noConversationsPlaceholder2.beGone()
        binding.searchHolder.beGone()
        refreshPage(position)
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu_main)
        binding.mainMenu.toggleHideOnScroll(true)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchClosedListener = {
            endSearch()
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            searchTextChanged(text)
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            handleMenuItemClick(menuItem)
        }
    }

    private fun handleMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return false
        }
        return true
    }

    /**
     * The search box is commons' `menu_search.xml`, which cannot be edited from here, so its look is
     * applied at runtime: the decorative magnifier / back-arrow on the left is dropped (focusing the
     * field opens the search, the system back key closes it) and the Settings / About menu lives at
     * the end of the tab row. The 1.0.7 clear button is gone as of 1.0.8 - the field searches as you
     * type instead (debounced in [searchTextChanged]).
     */
    private fun setupSearchBox() {
        val search = binding.mainMenu.binding

        search.topToolbarSearchIcon.visibility = View.GONE
        search.topToolbar.visibility = View.GONE

        // with no clear button on its right the field spans the whole box again
        (search.topToolbarSearch.layoutParams as RelativeLayout.LayoutParams).apply {
            removeRule(RelativeLayout.END_OF)
            removeRule(RelativeLayout.START_OF)
            marginStart = 0
            marginEnd = 0
        }

        // commons pins the box to ?attr/actionBarSize (56dp) with 16dp insets on every side and
        // lets the EditText keep its own minHeight, which is why it stayed bulky no matter what the
        // runtime colours did. The height is cut right down here, the EditText's minHeight is
        // cleared so it cannot stretch the box back open, and the *inner* inset is small - while the
        // box itself keeps the standard activity margin on both sides, so its left edge lines up
        // with the tab labels and the avatars of the rows below (1.0.8 rework + 1.1.01 spec).
        val density = resources.displayMetrics.density
        val boxPaddingH = (SEARCH_BOX_H_PADDING_DP * density).toInt()
        val boxMarginH = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        search.searchBarContainer.apply {
            layoutParams = layoutParams.apply {
                height = (SEARCH_BOX_HEIGHT_DP * density).toInt()
            }
            setPadding(boxMarginH, 0, boxMarginH, 0)
        }
        search.topToolbarSearch.apply {
            minimumHeight = 0
            setPadding(boxPaddingH, 0, 0, 0)
        }

        applySearchBoxColors()
    }

    /**
     * Re-applied after MySearchMenu.updateColors(): that call unconditionally puts a
     * `colorPrimary@25%` colour filter on whatever Drawable the container happens to have, which is
     * what used to leave the search box tinted instead of the #F2F2F2 the spec asks for.
     *
     * Assigning a *fresh* Drawable instance (rather than `setBackgroundResource`, which may hand
     * back an instance sharing the tinted constant state) is what makes this stick.
     */
    private fun applySearchBoxColors() {
        val search = binding.mainMenu.binding
        search.toolbarContainer.background =
            AppCompatResources.getDrawable(this, R.drawable.search_menu_background)
        search.topToolbarSearch.apply {
            // a CJK single line sits visually low with the default font padding
            includeFontPadding = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SEARCH_TEXT_SIZE_SP)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        }
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    /**
     * Re-binds the visible rows.
     *
     * Both [updateTextColors] (a window-wide repaint, run on every resume) and the
     * custom-appearance screen leave the lists showing stale colours / sizes, because DiffUtil
     * never re-binds a row whose content did not change - that is why the appearance settings
     * used to need an app restart, and why the avatars turned black after opening a
     * conversation (1.0.3 spec).
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun refreshListAppearance() {
        pageLists.forEach { it?.adapter?.notifyDataSetChanged() }
    }

    /**
     * [updateTextColors] repaints every TextView of the window, which used to leave the list
     * avatars with black text after coming back from a conversation (1.0.3 spec). The coloured
     * avatar background always needs white text, so re-assert it right away - the full re-bind
     * above only happens on the next layout pass.
     *
     * Both avatar ids are covered because a page can be showing either kind of row: the
     * conversation layout uses one id, the message row (starred tab, search results) the other.
     */
    private fun restoreAvatarTextColors() {
        pageLists.forEach { list ->
            if (list != null) {
                restoreAvatarTextColorsFor(list, R.id.conversation_avatar_text)
                restoreAvatarTextColorsFor(list, R.id.starred_avatar_text)
            }
        }
    }

    private fun restoreAvatarTextColorsFor(list: ViewGroup, avatarId: Int) {
        for (i in 0 until list.childCount) {
            val avatar = list.getChildAt(i).findViewById<TextView>(avatarId) ?: continue
            if (avatar.visibility == View.VISIBLE) {
                avatar.setTextColor(Color.WHITE)
            }
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
        applySearchBoxColors()
        // once more after the current pass: the tint is applied to whatever background the
        // container has at the time, so ours has to be the very last word (1.0.8 spec)
        binding.mainMenu.post { applySearchBoxColors() }
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional.
    // If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        setupTopTabs()
        getCachedConversations()
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getAll().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(conversations)
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val scan = scanConversations(privateContacts = privateContacts)
            val conversations = scan.conversations

            // A scan may only be used to delete cached conversations when it actually completed.
            // A scan that came back empty while conversations are cached means the scan itself
            // failed (provider hiccup, revoked permission, aborted cursor) rather than "every
            // single thread was deleted"; a scan that lost rows mid-way is just as unreliable.
            // Purging in either case hides conversations from the list and from the search
            // (1.0.4 / 1.0.5 spec).
            val scanIsUsable = scan.complete &&
                (conversations.isNotEmpty() || cachedConversations.isEmpty())

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread && scanIsUsable) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages
                    // to the new thread
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    // FIXME: Scheduled message date is being reset here. Conversations with
                    //  scheduled messages will have their original date.
                    insertOrUpdateConversation(conv)
                }
            }

            val allConversations = conversationsDB.getAll() as ArrayList<Conversation>
            runOnUiThread {
                setupConversations(allConversations)
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(threadId, includeScheduledMessages = false)
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    /**
     * Adapter for the ViewPager2: exactly three pages, one per tab. Pages are created on demand and
     * [bindPage] gives each one its own RecyclerView adapter when it gets bound.
     */
    private inner class TabPagerAdapter : RecyclerView.Adapter<TabPagerAdapter.PageHolder>() {
        inner class PageHolder(
            val scroller: RecyclerViewFastScroller,
            val list: MyRecyclerView,
        ) : RecyclerView.ViewHolder(scroller)

        override fun getItemCount() = TAB_COUNT

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = layoutInflater.inflate(R.layout.page_conversations, parent, false)
            val scroller = view as RecyclerViewFastScroller
            val list = view.findViewById<MyRecyclerView>(R.id.page_list)
            return PageHolder(scroller, list)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            bindPage(position, holder.scroller, holder.list)
        }
    }

    /**
     * Attaches an adapter to one pager page and hands it the data for that tab. Called whenever the
     * pager binds a page, which also covers the pages it recreates after a data set change.
     */
    private fun bindPage(tab: Int, scroller: RecyclerViewFastScroller, list: MyRecyclerView) {
        pageLists[tab] = list
        pageScrollers[tab] = scroller

        ensurePageAdapter(tab, list)
        scroller.updateColors(getProperPrimaryColor())
        refreshPage(tab)
    }

    /**
     * Makes sure a page holds the adapter its current content needs.
     *
     * The two conversation tabs render conversations normally, but message rows while a search is
     * running - a search result is a single message (1.0.9 spec) - so entering or leaving a search
     * replaces the page's adapter. Both kinds of message row reuse [StarredMessagesAdapter], which
     * renders exactly the conversation row layout off a [Message].
     */
    private fun ensurePageAdapter(tab: Int, list: MyRecyclerView) {
        val wantsMessageRows = tab == TAB_STARRED || searchActive
        val hasMessageRows = list.adapter is StarredMessagesAdapter
        if (list.adapter != null && wantsMessageRows == hasMessageRows) {
            return
        }

        hideKeyboard()
        list.adapter = when {
            tab == TAB_STARRED -> StarredMessagesAdapter(
                activity = this,
                recyclerView = list,
                itemClick = { handleStarredMessageClick(it) }
            ).apply { attachStarToggle() }

            wantsMessageRows -> StarredMessagesAdapter(
                activity = this,
                recyclerView = list,
                itemClick = { handleSearchMessageClick(it) }
            ).apply { attachStarToggle() }

            else -> ConversationsAdapter(
                activity = this,
                recyclerView = list,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )
        }

        if (areSystemAnimationsEnabled) {
            list.scheduleLayoutAnimation()
        }
    }

    /**
     * Routes a row's star toggle back into [starredMessageIds], the single source of truth for
     * the id set (1.1.04 fix). Before, only the adapter's own copy was updated, so the next
     * `refreshPage` pushed the stale activity set straight back and the star icon "reverted"
     * while the database was actually correct (review A1).
     */
    private fun StarredMessagesAdapter.attachStarToggle() {
        onStarToggledListener = { messageId, starred ->
            starredMessageIds = if (starred) {
                starredMessageIds + messageId
            } else {
                starredMessageIds - messageId
            }
        }
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        allConversationsForTabs = conversations
        invalidateConversationCache()
        refreshConversationList(cached)
    }

    /**
     * Re-renders all three tabs. Every path that used to call `setupConversations` ends up here, so
     * switching tabs (by tap or by swipe) keeps whatever is currently on screen - an active search
     * included - instead of silently falling back to the full list (1.0.7 spec).
     *
     * The conversation build itself is skipped whenever the cache already holds this tab, which is
     * what makes swiping between tabs cheap (1.0.8 spec).
     */
    private fun refreshConversationList(cached: Boolean = false) {
        for (tab in 0 until TAB_COUNT) {
            refreshPage(tab, cached)
        }
    }

    /**
     * Pushes one tab's data into its page.
     *
     * Every tab is refreshed - not just the visible one - so the page a drag is about to reveal
     * already holds its rows. The loading / empty hints are only touched for the visible tab, so a
     * background refresh can never flash a placeholder over the page being looked at (1.0.8 rework).
     *
     * While a search is running the two conversation tabs carry message rows instead of conversation
     * rows, so the page's adapter is swapped first (1.0.9 spec).
     */
    private fun refreshPage(tab: Int, cached: Boolean = false) {
        val list = pageLists[tab]
        if (list != null) {
            ensurePageAdapter(tab, list)
        }

        if (tab == TAB_STARRED) {
            // the starred tab has its own adapter, but it answers the same query (1.0.7 spec)
            loadStarredMessages()
            return
        }

        if (searchActive) {
            val results = searchMessagesForTab(tab)
            (list?.adapter as? StarredMessagesAdapter)?.apply {
                highlightText = lastSearchedText
                // these rows are search hits, not the starred tab, so every one of them carries a
                // star toggle and can be starred straight from the results (1.1.01 spec)
                showStarToggle = true
                starredMessageIds = this@MainActivity.starredMessageIds
            }?.submitList(results)

            if (tab == currentTab) {
                showOrHideProgress(false)
                showOrHidePlaceholder(results.isEmpty())
                updateSearchHolder(results.isEmpty())
            }
            return
        }

        val listed = conversationCache.getOrPut("$tab") { buildConversationList(tab) }

        // the page may not exist yet (ViewPager2 creates pages lazily): the list is still built and
        // cached here so the visible tab's loading / empty state is right immediately, and bindPage
        // picks the cached data up the moment the page appears
        list?.let {
            try {
                (it.adapter as? ConversationsAdapter)?.apply {
                    highlightText = ""
                    updateConversations(listed.conversations)
                }
            } catch (_: Exception) {
            }
        }

        if (tab == currentTab) {
            if (cached && config.appRunCount == 1) {
                // there are no cached conversations on the first run so we show the
                // loading placeholder and progress until we are done loading from telephony
                showOrHideProgress(listed.sourceEmpty)
            } else {
                showOrHideProgress(false)
                showOrHidePlaceholder(listed.empty)
            }
            updateSearchHolder(listed.empty)
        }
    }

    /**
     * The search hits that belong to [tab].
     *
     * A hit is filed under the tab its conversation already lives in, so the tab strip keeps
     * meaning the same thing inside and outside a search. Hits of a thread the conversation list
     * does not know about yet fall back to classifying the message itself.
     *
     * Both branches hand the classifier the contact-name flag: a sender saved in the address
     * book is a regular conversation even when its number looks service-like (1.1.04 fix).
     */
    private fun searchMessagesForTab(tab: Int): List<Message> {
        val conversationByThread = allConversationsForTabs.associateBy { it.threadId }
        return searchMessageResults.filter { message ->
            val conversation = conversationByThread[message.threadId]
            val isService = if (conversation != null) {
                isServiceConversation(
                    phoneNumber = conversation.phoneNumber,
                    snippet = conversation.snippet,
                    hasContactName = conversation.hasContactName(),
                )
            } else {
                isServiceConversation(
                    phoneNumber = message.senderPhoneNumber,
                    snippet = message.body,
                    hasContactName = message.senderName != message.senderPhoneNumber &&
                        AvatarColors.isRealName(message.senderName),
                )
            }
            if (tab == TAB_SERVICES) isService else !isService
        }
    }

    /**
     * Filters, sorts and caches the list for one tab. Split out of [refreshConversationList] so a
     * swipe between two tabs can reuse what it already built, and parameterised by tab so
     * [refreshPage] can build any of the three.
     */
    private fun buildConversationList(tab: Int): CachedConversationList {
        val filtered = filterConversationsForTab(allConversationsForTabs, tab)
        // the pinned set is read once: doing the lookup *inside* the comparator meant a
        // SharedPreferences read per comparison, which is what made tab switches stall
        val pinned = config.pinnedConversations
        val sorted = filtered
            .sortedWith(
                compareByDescending<Conversation> { pinned.contains(it.threadId.toString()) }
                    .thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>

        return CachedConversationList(
            conversations = sorted,
            empty = filtered.isEmpty(),
            sourceEmpty = allConversationsForTabs.isEmpty(),
        )
    }

    /** Drops every cached tab list; the next refresh rebuilds from scratch. */
    private fun invalidateConversationCache() {
        conversationCache.clear()
    }

    /**
     * True when the conversation's title is a real contact name, as opposed to a raw number or a
     * user-set custom title. A saved contact is always classified as a regular conversation,
     * even when its number looks service-like (1.1.04 fix); a custom title must not hide the
     * service-ness of the number behind it.
     */
    private fun Conversation.hasContactName(): Boolean {
        return !usesCustomTitle && AvatarColors.isRealName(title)
    }

    private fun filterConversationsForTab(
        conversations: ArrayList<Conversation>,
        tab: Int,
    ): ArrayList<Conversation> {
        return when (tab) {
            TAB_SERVICES -> ArrayList(
                conversations.filter {
                    isServiceConversation(it.phoneNumber, it.snippet, it.hasContactName())
                }
            )
            else -> ArrayList(
                conversations.filter {
                    !isServiceConversation(it.phoneNumber, it.snippet, it.hasContactName())
                }
            )
        }
    }

    /**
     * Injects the tab strip as the first child of the toolbar's AppBarLayout, so the tabs end up
     * *above* the search box (1.0.2 spec) while still being pushed below the status bar by the
     * app bar's own window-inset handling.
     */
    private fun setupTopTabs() {
        tabConversationsView?.let {
            updateTabColors()
            return
        }

        val tabs = layoutInflater.inflate(R.layout.main_tabs, binding.mainMenu, false)
        binding.mainMenu.addView(tabs, 0)
        tabConversationsView = tabs.findViewById(R.id.tab_conversations)
        tabServicesView = tabs.findViewById(R.id.tab_services)
        tabStarredView = tabs.findViewById(R.id.tab_starred)
        tabRowMenuView = tabs.findViewById(R.id.tab_row_menu)

        tabConversationsView?.setOnClickListener { switchTab(TAB_CONVERSATIONS) }
        tabServicesView?.setOnClickListener { switchTab(TAB_SERVICES) }
        tabStarredView?.setOnClickListener { switchTab(TAB_STARRED) }
        // Settings / About moved out of the search box to the end of this row (1.0.7 spec).
        // It is a custom popup rather than a PopupMenu: the theme's static text colour is
        // near-white (commons' AppTheme.Base derives from Theme.Material3.Dark and nothing
        // repaints a popup), so a stock PopupMenu showed invisible items on a light background
        // (1.0.8 spec).
        tabRowMenuView?.setOnClickListener { anchor -> showTabRowMenu(anchor) }
        updateTabColors()
    }

    /**
     * White popup anchored to the tab row's overflow button, holding Settings and About.
     *
     * Opaque background + elevation are what give it the drop shadow the 1.0.8 spec asks for:
     * the system derives a PopupWindow's shadow outline from its background drawable.
     */
    @Suppress("DEPRECATION")
    private fun showTabRowMenu(anchor: View) {
        val content = layoutInflater.inflate(R.layout.popup_main_menu, null)
        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            // API 36 removed getBackgroundDrawable(), so the Kotlin synthetic property
            // `backgroundDrawable` no longer resolves — must call the setter explicitly.
            setBackgroundDrawable(AppCompatResources.getDrawable(this@MainActivity, R.drawable.popup_menu_background))
            elevation = POPUP_MENU_ELEVATION_DP * resources.displayMetrics.density
            isOutsideTouchable = true
            isFocusable = true
        }

        content.findViewById<View>(R.id.popup_menu_settings).setOnClickListener {
            popup.dismiss()
            launchSettings()
        }
        content.findViewById<View>(R.id.popup_menu_about).setOnClickListener {
            popup.dismiss()
            launchAbout()
        }

        val gap = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        popup.showAsDropDown(anchor, 0, -gap, Gravity.END)
    }

    /** The selected tab uses the accent colour, the rest follow the theme (1.0.7 spec). */
    private fun updateTabColors() {
        val selectedColor = config.highlightColor
        val normalColor = getProperTextColor()
        tabConversationsView?.setTextColor(if (currentTab == TAB_CONVERSATIONS) selectedColor else normalColor)
        tabServicesView?.setTextColor(if (currentTab == TAB_SERVICES) selectedColor else normalColor)
        tabStarredView?.setTextColor(if (currentTab == TAB_STARRED) selectedColor else normalColor)
        tabRowMenuView?.applyColorFilter(normalColor)
    }

    /**
     * Tapping a tab simply asks the pager to slide to its page; the page-change callback then keeps
     * the strip and the surrounding chrome in sync, so a tap and a drag behave identically
     * (1.0.8 rework).
     */
    private fun switchTab(tab: Int) {
        if (currentTab == tab) {
            return
        }
        binding.conversationsPager.setCurrentItem(tab, true)
    }

    private fun loadStarredMessages() {
        ensureBackgroundThread {
            val messages = try {
                getStarredMessages()
            } catch (_: Exception) {
                emptyList<Message>()
            }

            // a search narrows the starred tab too, so all three tabs answer the same query
            val searchedText = if (searchActive) lastSearchedText else ""
            val filtered = if (searchedText.isEmpty()) {
                messages
            } else {
                messages.filter {
                    it.body.contains(searchedText, ignoreCase = true) ||
                        it.senderName.contains(searchedText, ignoreCase = true) ||
                        it.senderPhoneNumber.contains(searchedText, ignoreCase = true)
                }
            }

            runOnUiThread {
                val list = pageLists[TAB_STARRED] ?: return@runOnUiThread
                (list.adapter as? StarredMessagesAdapter)?.apply {
                    highlightText = searchedText
                    // the starred tab holds nothing but starred messages, so it needs no toggle
                    showStarToggle = false
                }?.submitList(filtered)

                if (currentTab == TAB_STARRED) {
                    showOrHidePlaceholder(filtered.isEmpty())
                }
            }
        }
    }

    /**
     * A starred row opens its conversation. The message it stands for travels along as
     * [SEARCHED_MESSAGE_ID], so the thread scrolls to that very message and highlights its bubble
     * instead of just opening at the bottom (1.0.9 spec).
     */
    private fun handleStarredMessageClick(any: Any) {
        val message = any as? Message ?: return
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, message.threadId)
            putExtra(THREAD_TITLE, message.senderName)
            putExtra(SEARCHED_MESSAGE_ID, message.id)
            startActivity(this)
        }
    }

    /**
     * A search result row stands for one message, so tapping it opens the thread at exactly that
     * message, with its bubble highlighted (1.0.9 spec).
     */
    private fun handleSearchMessageClick(any: Any) {
        val message = any as? Message ?: return
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, message.threadId)
            putExtra(THREAD_TITLE, message.senderName)
            putExtra(SEARCHED_MESSAGE_ID, message.id)
            startActivity(this)
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        // while a search is active the "no results" hint comes from the search holder instead
        val visible = show && !searchActive
        val isStarredTab = currentTab == TAB_STARRED
        binding.noConversationsPlaceholder.text =
            getString(if (isStarredTab) R.string.no_starred_messages else R.string.no_conversations_found)
        binding.noConversationsPlaceholder.beVisibleIf(visible)
        // the "start a conversation" shortcut only makes sense on the two conversation tabs
        binding.noConversationsPlaceholder2.beVisibleIf(visible && !isStarredTab)
    }

    /**
     * Shows the search hint overlay ("type 2 characters" while the query is too short, "no items
     * found" when nothing matched) and hides it as soon as there are results to look at - the
     * results themselves are rendered by the normal conversation list (1.0.7 spec).
     */
    /**
     * Shows the "no results" hint and hides it as soon as there are results to look at - the
     * results themselves are rendered by the normal conversation list (1.0.7 spec).
     *
     * The old "type at least 2 characters" state is gone: the search now runs on any non-empty
     * query (1.0.8 spec).
     */
    private fun updateSearchHolder(noResults: Boolean) {
        val showHolder = binding.mainMenu.isSearchOpen && noResults
        binding.searchPlaceholder.beVisibleIf(showHolder)
        binding.searchPlaceholder2.beGone()
        binding.searchHolder.alpha = 1f
        binding.searchHolder.beVisibleIf(showHolder)
    }

    private fun endSearch() {
        searchHandler.removeCallbacksAndMessages(null)
        searchActive = false
        lastSearchedText = ""
        searchMessageResults = emptyList()
        invalidateConversationCache()
        binding.searchHolder.beGone()
        binding.searchPlaceholder.beGone()
        binding.searchPlaceholder2.beGone()
        refreshConversationList()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        pageLists.forEach { it?.adapter?.notifyDataSetChanged() }
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(
            org.fossify.commons.R.id.shortcut_plus_background
        ).applyColorFilter(appIconColor)

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    /**
     * Debounced entry point for search-box input (1.0.8 spec: 1s).
     *
     * Every keystroke cancels the pending query, so the telephony provider is only hit once the
     * user stops typing. There is no minimum length any more: an empty box restores the plain list
     * and anything else is searched for as-is.
     */
    private fun searchTextChanged(text: String) {
        if (!binding.mainMenu.isSearchOpen) {
            return
        }

        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        val runnable = Runnable { runSearch(text) }
        pendingSearch = runnable
        searchHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
    }

    /**
     * Runs the query and re-renders the tabs.
     *
     * The hits are individual messages, taken straight from the telephony provider, so a thread
     * matches even when the term only appears in a message older than the one shown in the list -
     * and every matching message gets its own row (1.0.9 spec). The rows are drawn by the same
     * adapter the starred tab uses, which is what keeps the UI identical to a normal list row.
     */
    private fun runSearch(text: String) {
        lastSearchedText = text
        invalidateConversationCache()

        if (text.isEmpty()) {
            searchActive = false
            searchMessageResults = emptyList()
            refreshConversationList()
            return
        }

        searchActive = true
        ensureBackgroundThread {
            val messages: List<Message> = try {
                searchMessages(text)
            } catch (_: Exception) {
                emptyList()
            }
            // resolved here, off the main thread: the rows read it while binding (1.1.01 spec)
            val starIds = try {
                getStarredMessageIds()
            } catch (_: Exception) {
                emptySet<Long>()
            }

            if (text == lastSearchedText) {
                // newest first, matching how every other list is ordered
                searchMessageResults = messages.sortedWith(
                    compareByDescending<Message> { it.date }.thenByDescending { it.id }
                )
                runOnUiThread {
                    starredMessageIds = starIds
                    refreshConversationList()
                }
            }
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_2_title,
                text = R.string.faq_2_text
            ),
            FAQItem(
                title = R.string.faq_3_title,
                text = R.string.faq_3_text
            ),
            FAQItem(
                title = R.string.faq_4_title,
                text = R.string.faq_4_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    /**
     * Handles the conversation-refresh event.
     *
     * When the event carries thread ids (1.1.04: posted on leaving the thread screen, on
     * marking a thread unread and when a thread's unread state changes), only those threads are
     * re-read - the provider trigger in [updateLastConversationMessage] refreshes their snippets,
     * and the list rebuilds from the conversations DB. That replaces the full telephony scan
     * ([initMessenger]) every conversation exit used to trigger, which was the main source of
     * jank on returning to the list (review M1).
     *
     * An event without ids (receivers, bulk operations, settings changes) still runs the full
     * initialisation path.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(event: Events.RefreshConversations) {
        val threadIds = event.threadIds
        if (threadIds.isEmpty()) {
            initMessenger()
            return
        }

        ensureBackgroundThread {
            updateLastConversationMessage(threadIds)
            val all = try {
                conversationsDB.getAll().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }
            runOnUiThread {
                setupConversations(all)
            }
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
