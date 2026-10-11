package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import kotlin.math.abs
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
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
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.StarredMessagesAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.scanConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.searchMessages
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
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

/** Minimum horizontal travel for a fling to count as "switch tab" rather than a scroll. */
private const val SWIPE_MIN_DISTANCE_DP = 72f

/** Search box / hint text; commons' `big_text_size` (18sp) minus 2 (1.0.7 spec). */
private const val SEARCH_TEXT_SIZE_SP = 16f

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
     * Search state. Matching conversations are shown in the *same* list as the tabs (1.0.7 spec)
     * so a result row looks exactly like a normal row and the tab strip keeps filtering it.
     * [searchThreadIds]/[searchBodies] come from the telephony provider and let a thread match on
     * a message other than its newest one, in which case that message becomes the shown snippet.
     */
    private var searchActive = false
    private var searchThreadIds: Set<Long> = emptySet()
    private var searchBodies: Map<Long, String> = emptyMap()
    private var searchMessageIds: Map<Long, Long> = emptyMap()

    private var tabConversationsView: TextView? = null
    private var tabServicesView: TextView? = null
    private var tabStarredView: TextView? = null
    private var tabRowMenuView: ImageView? = null

    /** Horizontal-swipe support for switching between the three tabs (1.0.7 spec). */
    private val swipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) {
                    return false
                }

                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val minDistance = SWIPE_MIN_DISTANCE_DP * resources.displayMetrics.density
                if (abs(dx) < minDistance || abs(dx) < abs(dy) * 1.5f) {
                    return false
                }

                switchTabRelative(if (dx < 0) 1 else -1)
                return true
            }
        })
    }

    private val swipeTouchListener = View.OnTouchListener { _, event ->
        // feed the detector but never consume: vertical scrolling must keep working untouched
        swipeDetector.onTouchEvent(event)
        false
    }

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        setupSearchBox()
        setupTabSwiping()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))

        clearAllMessagesIfNeeded {
            loadMessages()
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()

        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())
        updateTabColors()

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
        refreshListAppearance()
        restoreAvatarTextColors()
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
     * Left/right fling on either list switches tab. The listener never consumes the event, so
     * vertical scrolling and the search-mode list behave exactly as before; it also covers the
     * search results because they are rendered by the very same list (1.0.7 spec).
     */
    private fun setupTabSwiping() {
        binding.conversationsList.setOnTouchListener(swipeTouchListener)
        binding.starredList.setOnTouchListener(swipeTouchListener)
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
     * The search box is commons' `menu_search.xml`, which cannot be edited from here, so its 1.0.7
     * look is applied at runtime: the decorative magnifier / back-arrow on the left is dropped
     * (focusing the field opens the search, the system back key closes it), the Settings / About
     * menu moves to the end of the tab row and a clear button is added on the right.
     */
    private fun setupSearchBox() {
        val search = binding.mainMenu.binding

        search.topToolbarSearchIcon.visibility = View.GONE
        search.topToolbar.visibility = View.GONE

        val clearButton = ImageView(this).apply {
            id = R.id.search_clear_button
            setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
            contentDescription = getString(org.fossify.commons.R.string.close)
            scaleType = ImageView.ScaleType.CENTER
            val padding = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
            setPadding(padding, padding, padding, padding)
            background = borderlessSelectableBackground()
            setOnClickListener {
                // an empty query cannot be cleared, so it closes the search instead
                if (search.topToolbarSearch.text.isNullOrEmpty()) {
                    binding.mainMenu.closeSearch()
                } else {
                    search.topToolbarSearch.setText("")
                }
            }
        }
        search.toolbarContainer.addView(
            clearButton,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }
        )

        // the field no longer sits next to the (hidden) icon, and must stop before the clear button
        (search.topToolbarSearch.layoutParams as RelativeLayout.LayoutParams).apply {
            removeRule(RelativeLayout.END_OF)
            removeRule(RelativeLayout.START_OF)
            addRule(RelativeLayout.START_OF, R.id.search_clear_button)
            marginStart = 0
            marginEnd = 0
        }
        val textInset = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        search.topToolbarSearch.setPadding(
            textInset,
            search.topToolbarSearch.paddingTop,
            0,
            search.topToolbarSearch.paddingBottom
        )

        applySearchBoxColors()
    }

    /**
     * Re-applied after MySearchMenu.updateColors(): that call unconditionally tints the library's
     * own search-box background with colorPrimary@25%, so the 1.0.7 background (rounded square,
     * conversation-page body colour) has to be re-declared every time.
     */
    private fun applySearchBoxColors() {
        val search = binding.mainMenu.binding
        search.toolbarContainer.setBackgroundResource(R.drawable.search_box_background)
        search.topToolbarSearch.apply {
            // a CJK single line sits visually low with the default font padding
            includeFontPadding = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SEARCH_TEXT_SIZE_SP)
        }
        search.toolbarContainer.findViewById<ImageView>(R.id.search_clear_button)
            ?.applyColorFilter(getProperTextColor())
    }

    private fun borderlessSelectableBackground(): Drawable? {
        val value = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)) {
            AppCompatResources.getDrawable(this, value.resourceId)
        } else {
            null
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
        binding.conversationsList.adapter?.notifyDataSetChanged()
        binding.starredList.adapter?.notifyDataSetChanged()
    }

    /**
     * [updateTextColors] repaints every TextView of the window, which used to leave the list
     * avatars with black text after coming back from a conversation (1.0.3 spec). The coloured
     * avatar background always needs white text, so re-assert it right away - the full re-bind
     * above only happens on the next layout pass.
     */
    private fun restoreAvatarTextColors() {
        restoreAvatarTextColorsFor(binding.conversationsList, R.id.conversation_avatar_text)
        restoreAvatarTextColorsFor(binding.starredList, R.id.starred_avatar_text)
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

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        allConversationsForTabs = conversations
        refreshConversationList(cached)
    }

    /**
     * Renders the list for the current tab, using the search matches as the source while a search
     * is active. Every path that used to call `setupConversations` now ends up here, so switching
     * tabs (by tap or by swipe) keeps the active search applied - a result list for the
     * "notifications" tab is never silently replaced by the full one (1.0.7 spec).
     */
    private fun refreshConversationList(cached: Boolean = false) {
        if (currentTab == TAB_STARRED) {
            // the starred tab has its own adapter, but it answers the same query (1.0.7 spec)
            loadStarredMessages()
            return
        }

        val source = if (searchActive) computeSearchMatches() else allConversationsForTabs
        val filteredConversations = filterConversationsForTab(source)
        val sortedConversations = filteredConversations
            .sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>

        if (cached && config.appRunCount == 1) {
            // there are no cached conversations on the first run so we show the
            // loading placeholder and progress until we are done loading from telephony
            showOrHideProgress(source.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(filteredConversations.isEmpty())
        }
        updateSearchHolder(filteredConversations.isEmpty())

        try {
            getOrCreateConversationsAdapter().apply {
                highlightText = if (searchActive) lastSearchedText else ""
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Conversations matching the active search: title / number / shown snippet, plus any thread
     * that has at least one message containing the term. For the latter the matching message
     * body is promoted to the shown snippet, otherwise the hit would be invisible.
     */
    private fun computeSearchMatches(): ArrayList<Conversation> {
        val text = lastSearchedText
        val matches = ArrayList<Conversation>()
        if (text.isEmpty()) {
            return matches
        }

        allConversationsForTabs.forEach { conversation ->
            val titleHit = conversation.title.contains(text, ignoreCase = true) ||
                conversation.phoneNumber.contains(text, ignoreCase = true)
            val snippetHit = conversation.snippet.contains(text, ignoreCase = true)
            val bodyHit = searchThreadIds.contains(conversation.threadId)
            if (titleHit || snippetHit || bodyHit) {
                val snippet = if (!snippetHit && bodyHit) {
                    searchBodies[conversation.threadId] ?: conversation.snippet
                } else {
                    conversation.snippet
                }
                matches.add(if (snippet != conversation.snippet) conversation.copy(snippet = snippet) else conversation)
            }
        }
        return matches
    }

    private fun filterConversationsForTab(
        conversations: ArrayList<Conversation>,
    ): ArrayList<Conversation> {
        return when (currentTab) {
            TAB_SERVICES -> ArrayList(
                conversations.filter { isServiceConversation(it.phoneNumber, it.snippet) }
            )
            else -> ArrayList(
                conversations.filter { !isServiceConversation(it.phoneNumber, it.snippet) }
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
        // Settings / About moved out of the search box to the end of this row (1.0.7 spec)
        tabRowMenuView?.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menuInflater.inflate(R.menu.menu_main, menu)
                setOnMenuItemClickListener { handleMenuItemClick(it) }
                show()
            }
        }
        updateTabColors()
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

    private fun switchTab(tab: Int) {
        if (currentTab == tab) {
            return
        }

        currentTab = tab
        val isStarredTab = tab == TAB_STARRED

        updateTabColors()

        binding.conversationsList.beVisibleIf(!isStarredTab)
        binding.conversationsProgressBar.beGone()
        binding.conversationsFastscroller.beVisibleIf(!isStarredTab)
        binding.conversationsFab.beVisibleIf(!isStarredTab)
        binding.starredList.beVisibleIf(isStarredTab)

        binding.noConversationsPlaceholder.beGone()
        binding.noConversationsPlaceholder2.beGone()
        binding.noStarredPlaceholder.beGone()

        if (isStarredTab) {
            loadStarredMessages()
        } else {
            refreshConversationList()
        }
    }

    /** Moves one tab left/right; swipe gestures use this (1.0.7 spec). */
    private fun switchTabRelative(delta: Int) {
        val next = currentTab + delta
        if (next in TAB_CONVERSATIONS..TAB_STARRED) {
            switchTab(next)
        }
    }

    private fun loadStarredMessages() {
        ensureBackgroundThread {
            val messages = try {
                messagesDB.getStarredMessages()
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
                val isEmpty = filtered.isEmpty()
                binding.starredList.beVisibleIf(!isEmpty)
                binding.noStarredPlaceholder.beVisibleIf(isEmpty)
                getOrCreateStarredAdapter().apply { highlightText = searchedText }.submitList(filtered)
            }
        }
    }

    private fun getOrCreateStarredAdapter(): StarredMessagesAdapter {
        var currAdapter = binding.starredList.adapter
        if (currAdapter == null) {
            currAdapter = StarredMessagesAdapter(
                activity = this,
                recyclerView = binding.starredList,
                itemClick = { handleStarredMessageClick(it) }
            )
            binding.starredList.adapter = currAdapter
        }
        return currAdapter as StarredMessagesAdapter
    }

    private fun handleStarredMessageClick(any: Any) {
        val message = any as? Message ?: return
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, message.threadId)
            putExtra(THREAD_TITLE, message.senderName)
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
        binding.conversationsFastscroller.beGoneIf(visible)
        binding.noConversationsPlaceholder.beVisibleIf(visible)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
        binding.noConversationsPlaceholder2.beVisibleIf(visible)
    }

    /**
     * Shows the search hint overlay ("type 2 characters" while the query is too short, "no items
     * found" when nothing matched) and hides it as soon as there are results to look at - the
     * results themselves are rendered by the normal conversation list (1.0.7 spec).
     */
    private fun updateSearchHolder(noResults: Boolean) {
        val tooShort = lastSearchedText.length < 2
        val showHolder = binding.mainMenu.isSearchOpen && (tooShort || noResults)
        binding.searchPlaceholder.beVisibleIf(showHolder && !tooShort)
        binding.searchPlaceholder2.beVisibleIf(showHolder && tooShort)
        binding.searchHolder.alpha = 1f
        binding.searchHolder.beVisibleIf(showHolder)
    }

    private fun endSearch() {
        searchActive = false
        lastSearchedText = ""
        searchThreadIds = emptySet()
        searchBodies = emptyMap()
        searchMessageIds = emptyMap()
        binding.searchHolder.beGone()
        binding.searchPlaceholder.beGone()
        binding.searchPlaceholder2.beGone()
        refreshConversationList()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            // a search hit may sit on an older message than the shown snippet - jump right to it
            putExtra(SEARCHED_MESSAGE_ID, searchMessageIds[conversation.threadId] ?: -1L)
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
     * Runs the query and re-renders the normal list. Results are no longer a separate list of
     * mixed "conversation" / "message" rows: every hit becomes a row of the current tab, drawn by
     * the conversation adapter, so the tabs keep working and the match is highlighted in place
     * (1.0.7 spec).
     */
    private fun searchTextChanged(text: String) {
        if (!binding.mainMenu.isSearchOpen) {
            return
        }

        lastSearchedText = text
        if (text.length < 2) {
            searchActive = false
            searchThreadIds = emptySet()
            searchBodies = emptyMap()
            searchMessageIds = emptyMap()
            refreshConversationList()
            return
        }

        searchActive = true
        ensureBackgroundThread {
            // the messages come straight from the telephony provider, so a thread matches even
            // when the term only appears in a message older than the one shown in the list
            val messages: List<Message> = try {
                searchMessages(text)
            } catch (_: Exception) {
                emptyList()
            }

            val threadIds = HashSet<Long>()
            val bodies = LinkedHashMap<Long, String>()
            val messageIds = LinkedHashMap<Long, Long>()
            // newest first, so the first hit per thread is the one we keep
            messages.sortedByDescending { it.id }.forEach { message ->
                threadIds.add(message.threadId)
                messageIds.putIfAbsent(message.threadId, message.id)
                if (message.body.isNotEmpty()) {
                    bodies.putIfAbsent(message.threadId, message.body)
                }
            }

            if (text == lastSearchedText) {
                searchThreadIds = threadIds
                searchBodies = bodies
                searchMessageIds = messageIds
                runOnUiThread { refreshConversationList() }
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
