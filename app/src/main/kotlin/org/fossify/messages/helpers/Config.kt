package org.fossify.messages.helpers

import android.content.Context
import android.content.res.Configuration
import org.fossify.commons.helpers.BaseConfig
import org.fossify.messages.extensions.getDefaultKeyboardHeight
import org.fossify.messages.models.Conversation

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit()
            .putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit().putBoolean(SEND_ON_ENTER, sendOnEnter).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit()
            .putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit()
            .putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    val isGroupMessageMmsPreferenceResolved: Boolean
        get() = prefs.contains(SEND_GROUP_MESSAGE_MMS)

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit()
            .putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_200_KB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit()
            .putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations =
            pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(BLOCKED_KEYWORDS, HashSet<String>())!!
        set(blockedKeywords) = prefs.edit().putStringSet(BLOCKED_KEYWORDS, blockedKeywords).apply()

    fun addBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.plus(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.minus(keyword)
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    // --- theme mode (1.0.9 spec) ---------------------------------------------------

    /**
     * Follow the device, always light or always dark. Defaults to light, as the 1.0.9 spec asks -
     * until 1.0.9 the look was whatever commons' dynamic theme produced, i.e. it followed the
     * device, which on a dark device gave a dark app without the user ever choosing it.
     */
    var themeMode: Int
        get() = prefs.getInt(THEME_MODE, DEFAULT_THEME_MODE)
        set(value) = prefs.edit().putInt(THEME_MODE, value).apply()

    /**
     * True when the dark half of every colour pair is the one on screen. [THEME_MODE_SYSTEM] defers
     * to the device; the forced modes are what actually make the two-colour-set feature usable,
     * since they are the only modes where the user's choice does not depend on the device setting.
     *
     * The device state is read off the resources configuration rather than
     * [android.content.res.Configuration] of the system, because `AppCompatDelegate`'s forced night
     * mode rewrites exactly this value - that is how a forced theme reaches the app.
     */
    val isDarkAppearance: Boolean
        get() = when (themeMode) {
            THEME_MODE_LIGHT -> false
            THEME_MODE_DARK -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

    /**
     * Re-applies the 1.0.9 default text sizes once. A changed default is invisible to an install
     * that already stored the old one, and the spec re-defined them, so the stored sizes are
     * cleared once (colours are left alone - they got their own light/dark keys anyway).
     */
    fun migrateAppearanceDefaults() {
        if (prefs.getBoolean(APPEARANCE_109_MIGRATED, false)) {
            return
        }

        prefs.edit()
            .putInt(HOME_TITLE_TEXT_SIZE, DEFAULT_HOME_TITLE_TEXT_SIZE)
            .putInt(HOME_SNIPPET_TEXT_SIZE, DEFAULT_HOME_SNIPPET_TEXT_SIZE)
            .putInt(HOME_INFO_TEXT_SIZE, DEFAULT_HOME_INFO_TEXT_SIZE)
            .putInt(COPYABLE_TEXT_SIZE, DEFAULT_COPYABLE_TEXT_SIZE)
            .putInt(THREAD_SNIPPET_TEXT_SIZE, DEFAULT_THREAD_SNIPPET_TEXT_SIZE)
            .putInt(THREAD_INFO_TEXT_SIZE, DEFAULT_THREAD_INFO_TEXT_SIZE)
            .putBoolean(APPEARANCE_109_MIGRATED, true)
            .apply()
    }

    // --- custom appearance: sizes are stored in sp, colors as ARGB ints -------------
    //
    // Sizes are shared by the light and the dark theme. Every colour exists twice: the plain
    // property reads/writes whichever half is currently in use, and the `…Light` / `…Dark` pair
    // behind it lets the appearance screen edit both halves regardless of the active theme
    // (1.0.9 spec).

    // home list: conversation title
    var homeTitleTextSize: Int
        get() = prefs.getInt(HOME_TITLE_TEXT_SIZE, DEFAULT_HOME_TITLE_TEXT_SIZE)
        set(value) = prefs.edit().putInt(HOME_TITLE_TEXT_SIZE, value).apply()

    var homeTitleTextColor: Int
        get() = if (isDarkAppearance) homeTitleTextColorDark else homeTitleTextColorLight
        set(value) {
            if (isDarkAppearance) homeTitleTextColorDark = value else homeTitleTextColorLight = value
        }

    var homeTitleTextColorLight: Int
        get() = prefs.getInt(HOME_TITLE_TEXT_COLOR, DEFAULT_HOME_TITLE_TEXT_COLOR)
        set(value) = prefs.edit().putInt(HOME_TITLE_TEXT_COLOR, value).apply()

    var homeTitleTextColorDark: Int
        get() = prefs.getInt(HOME_TITLE_TEXT_COLOR_DARK, DEFAULT_HOME_TITLE_TEXT_COLOR_DARK)
        set(value) = prefs.edit().putInt(HOME_TITLE_TEXT_COLOR_DARK, value).apply()

    // home list: message snippet
    var homeSnippetTextSize: Int
        get() = prefs.getInt(HOME_SNIPPET_TEXT_SIZE, DEFAULT_HOME_SNIPPET_TEXT_SIZE)
        set(value) = prefs.edit().putInt(HOME_SNIPPET_TEXT_SIZE, value).apply()

    var homeSnippetTextColor: Int
        get() = if (isDarkAppearance) homeSnippetTextColorDark else homeSnippetTextColorLight
        set(value) {
            if (isDarkAppearance) homeSnippetTextColorDark = value else homeSnippetTextColorLight = value
        }

    var homeSnippetTextColorLight: Int
        get() = prefs.getInt(HOME_SNIPPET_TEXT_COLOR, DEFAULT_HOME_SNIPPET_TEXT_COLOR)
        set(value) = prefs.edit().putInt(HOME_SNIPPET_TEXT_COLOR, value).apply()

    var homeSnippetTextColorDark: Int
        get() = prefs.getInt(HOME_SNIPPET_TEXT_COLOR_DARK, DEFAULT_HOME_SNIPPET_TEXT_COLOR_DARK)
        set(value) = prefs.edit().putInt(HOME_SNIPPET_TEXT_COLOR_DARK, value).apply()

    // home list: date / extra info
    var homeInfoTextSize: Int
        get() = prefs.getInt(HOME_INFO_TEXT_SIZE, DEFAULT_HOME_INFO_TEXT_SIZE)
        set(value) = prefs.edit().putInt(HOME_INFO_TEXT_SIZE, value).apply()

    var homeInfoTextColor: Int
        get() = if (isDarkAppearance) homeInfoTextColorDark else homeInfoTextColorLight
        set(value) {
            if (isDarkAppearance) homeInfoTextColorDark = value else homeInfoTextColorLight = value
        }

    var homeInfoTextColorLight: Int
        get() = prefs.getInt(HOME_INFO_TEXT_COLOR, DEFAULT_HOME_INFO_TEXT_COLOR)
        set(value) = prefs.edit().putInt(HOME_INFO_TEXT_COLOR, value).apply()

    var homeInfoTextColorDark: Int
        get() = prefs.getInt(HOME_INFO_TEXT_COLOR_DARK, DEFAULT_HOME_INFO_TEXT_COLOR_DARK)
        set(value) = prefs.edit().putInt(HOME_INFO_TEXT_COLOR_DARK, value).apply()

    // copyable strings / verification codes
    var copyableTextSize: Int
        get() = prefs.getInt(COPYABLE_TEXT_SIZE, DEFAULT_COPYABLE_TEXT_SIZE)
        set(value) = prefs.edit().putInt(COPYABLE_TEXT_SIZE, value).apply()

    var copyableTextColor: Int
        get() = if (isDarkAppearance) copyableTextColorDark else copyableTextColorLight
        set(value) {
            if (isDarkAppearance) copyableTextColorDark = value else copyableTextColorLight = value
        }

    var copyableTextColorLight: Int
        get() = prefs.getInt(COPYABLE_TEXT_COLOR, DEFAULT_COPYABLE_TEXT_COLOR)
        set(value) = prefs.edit().putInt(COPYABLE_TEXT_COLOR, value).apply()

    var copyableTextColorDark: Int
        get() = prefs.getInt(COPYABLE_TEXT_COLOR_DARK, DEFAULT_COPYABLE_TEXT_COLOR_DARK)
        set(value) = prefs.edit().putInt(COPYABLE_TEXT_COLOR_DARK, value).apply()

    // thread view: message body
    var threadSnippetTextSize: Int
        get() = prefs.getInt(THREAD_SNIPPET_TEXT_SIZE, DEFAULT_THREAD_SNIPPET_TEXT_SIZE)
        set(value) = prefs.edit().putInt(THREAD_SNIPPET_TEXT_SIZE, value).apply()

    var threadSnippetTextColor: Int
        get() = if (isDarkAppearance) threadSnippetTextColorDark else threadSnippetTextColorLight
        set(value) {
            if (isDarkAppearance) threadSnippetTextColorDark = value else threadSnippetTextColorLight = value
        }

    var threadSnippetTextColorLight: Int
        get() = prefs.getInt(THREAD_SNIPPET_TEXT_COLOR, DEFAULT_HOME_SNIPPET_TEXT_COLOR)
        set(value) = prefs.edit().putInt(THREAD_SNIPPET_TEXT_COLOR, value).apply()

    var threadSnippetTextColorDark: Int
        get() = prefs.getInt(THREAD_SNIPPET_TEXT_COLOR_DARK, DEFAULT_HOME_SNIPPET_TEXT_COLOR_DARK)
        set(value) = prefs.edit().putInt(THREAD_SNIPPET_TEXT_COLOR_DARK, value).apply()

    // thread view: date / sender info
    var threadInfoTextSize: Int
        get() = prefs.getInt(THREAD_INFO_TEXT_SIZE, DEFAULT_THREAD_INFO_TEXT_SIZE)
        set(value) = prefs.edit().putInt(THREAD_INFO_TEXT_SIZE, value).apply()

    var threadInfoTextColor: Int
        get() = if (isDarkAppearance) threadInfoTextColorDark else threadInfoTextColorLight
        set(value) {
            if (isDarkAppearance) threadInfoTextColorDark = value else threadInfoTextColorLight = value
        }

    var threadInfoTextColorLight: Int
        get() = prefs.getInt(THREAD_INFO_TEXT_COLOR, DEFAULT_HOME_INFO_TEXT_COLOR)
        set(value) = prefs.edit().putInt(THREAD_INFO_TEXT_COLOR, value).apply()

    var threadInfoTextColorDark: Int
        get() = prefs.getInt(THREAD_INFO_TEXT_COLOR_DARK, DEFAULT_HOME_INFO_TEXT_COLOR_DARK)
        set(value) = prefs.edit().putInt(THREAD_INFO_TEXT_COLOR_DARK, value).apply()

    // links inside message bodies (default matches the Android link blue)
    var linkTextColor: Int
        get() = if (isDarkAppearance) linkTextColorDark else linkTextColorLight
        set(value) {
            if (isDarkAppearance) linkTextColorDark = value else linkTextColorLight = value
        }

    var linkTextColorLight: Int
        get() = prefs.getInt(LINK_TEXT_COLOR, DEFAULT_LINK_TEXT_COLOR)
        set(value) = prefs.edit().putInt(LINK_TEXT_COLOR, value).apply()

    var linkTextColorDark: Int
        get() = prefs.getInt(LINK_TEXT_COLOR_DARK, DEFAULT_LINK_TEXT_COLOR_DARK)
        set(value) = prefs.edit().putInt(LINK_TEXT_COLOR_DARK, value).apply()

    // accent colour: the selected top tab, the search-result hit highlighting and the "delivered"
    // check mark (1.0.7 spec). Its light default is deliberately the copyable-string colour, so
    // turning the accent on does not change the existing look.
    var highlightColor: Int
        get() = if (isDarkAppearance) highlightColorDark else highlightColorLight
        set(value) {
            if (isDarkAppearance) highlightColorDark = value else highlightColorLight = value
        }

    var highlightColorLight: Int
        get() = prefs.getInt(HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR)
        set(value) = prefs.edit().putInt(HIGHLIGHT_COLOR, value).apply()

    var highlightColorDark: Int
        get() = prefs.getInt(HIGHLIGHT_COLOR_DARK, DEFAULT_HIGHLIGHT_COLOR_DARK)
        set(value) = prefs.edit().putInt(HIGHLIGHT_COLOR_DARK, value).apply()

    // backgrounds (1.1.09 spec): the app-wide surface - top bar, list, popup menu - and the
    // conversation page body, which the search box shares. Same light/dark pairing as the rest.
    var defaultBackgroundColor: Int
        get() = if (isDarkAppearance) defaultBackgroundColorDark else defaultBackgroundColorLight
        set(value) {
            if (isDarkAppearance) defaultBackgroundColorDark = value else defaultBackgroundColorLight = value
        }

    var defaultBackgroundColorLight: Int
        get() = prefs.getInt(DEFAULT_BACKGROUND_COLOR, DEFAULT_DEFAULT_BACKGROUND_COLOR)
        set(value) = prefs.edit().putInt(DEFAULT_BACKGROUND_COLOR, value).apply()

    var defaultBackgroundColorDark: Int
        get() = prefs.getInt(DEFAULT_BACKGROUND_COLOR_DARK, DEFAULT_DEFAULT_BACKGROUND_COLOR_DARK)
        set(value) = prefs.edit().putInt(DEFAULT_BACKGROUND_COLOR_DARK, value).apply()

    var threadBackgroundColor: Int
        get() = if (isDarkAppearance) threadBackgroundColorDark else threadBackgroundColorLight
        set(value) {
            if (isDarkAppearance) threadBackgroundColorDark = value else threadBackgroundColorLight = value
        }

    var threadBackgroundColorLight: Int
        get() = prefs.getInt(THREAD_BACKGROUND_COLOR, DEFAULT_THREAD_BACKGROUND_COLOR)
        set(value) = prefs.edit().putInt(THREAD_BACKGROUND_COLOR, value).apply()

    var threadBackgroundColorDark: Int
        get() = prefs.getInt(THREAD_BACKGROUND_COLOR_DARK, DEFAULT_THREAD_BACKGROUND_COLOR_DARK)
        set(value) = prefs.edit().putInt(THREAD_BACKGROUND_COLOR_DARK, value).apply()

    // the received message bubble (1.1.09 spec): it used to follow the theme's own background,
    // which is why it now has its own light/dark pair like the two surfaces above
    var receivedBubbleBackgroundColor: Int
        get() = if (isDarkAppearance) receivedBubbleBackgroundColorDark else receivedBubbleBackgroundColorLight
        set(value) {
            if (isDarkAppearance) receivedBubbleBackgroundColorDark = value else receivedBubbleBackgroundColorLight = value
        }

    var receivedBubbleBackgroundColorLight: Int
        get() = prefs.getInt(RECEIVED_BUBBLE_BACKGROUND_COLOR, DEFAULT_RECEIVED_BUBBLE_BACKGROUND_COLOR)
        set(value) = prefs.edit().putInt(RECEIVED_BUBBLE_BACKGROUND_COLOR, value).apply()

    var receivedBubbleBackgroundColorDark: Int
        get() = prefs.getInt(RECEIVED_BUBBLE_BACKGROUND_COLOR_DARK, DEFAULT_RECEIVED_BUBBLE_BACKGROUND_COLOR_DARK)
        set(value) = prefs.edit().putInt(RECEIVED_BUBBLE_BACKGROUND_COLOR_DARK, value).apply()

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit()
            .putStringSet(CUSTOM_NOTIFICATIONS, customNotifications).apply()

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }

    var lastBlockedKeywordExportPath: String
        get() = prefs.getString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit()
            .putString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, lastBlockedNumbersExportPath).apply()
}
