package org.fossify.messages.helpers

import android.content.Context
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

    // --- custom appearance: sizes are stored in sp, colors as ARGB ints -------------

    // home list: conversation title
    var homeTitleTextSize: Int
        get() = prefs.getInt(HOME_TITLE_TEXT_SIZE, 14)
        set(value) = prefs.edit().putInt(HOME_TITLE_TEXT_SIZE, value).apply()

    var homeTitleTextColor: Int
        get() = prefs.getInt(HOME_TITLE_TEXT_COLOR, 0xff000000.toInt())
        set(value) = prefs.edit().putInt(HOME_TITLE_TEXT_COLOR, value).apply()

    // home list: message snippet
    var homeSnippetTextSize: Int
        get() = prefs.getInt(HOME_SNIPPET_TEXT_SIZE, 12)
        set(value) = prefs.edit().putInt(HOME_SNIPPET_TEXT_SIZE, value).apply()

    var homeSnippetTextColor: Int
        get() = prefs.getInt(HOME_SNIPPET_TEXT_COLOR, 0xff555555.toInt())
        set(value) = prefs.edit().putInt(HOME_SNIPPET_TEXT_COLOR, value).apply()

    // home list: date / extra info
    var homeInfoTextSize: Int
        get() = prefs.getInt(HOME_INFO_TEXT_SIZE, 10)
        set(value) = prefs.edit().putInt(HOME_INFO_TEXT_SIZE, value).apply()

    var homeInfoTextColor: Int
        get() = prefs.getInt(HOME_INFO_TEXT_COLOR, 0xff999999.toInt())
        set(value) = prefs.edit().putInt(HOME_INFO_TEXT_COLOR, value).apply()

    // copyable strings / verification codes
    var copyableTextSize: Int
        get() = prefs.getInt(COPYABLE_TEXT_SIZE, 12)
        set(value) = prefs.edit().putInt(COPYABLE_TEXT_SIZE, value).apply()

    var copyableTextColor: Int
        get() = prefs.getInt(COPYABLE_TEXT_COLOR, 0xff4caf50.toInt())
        set(value) = prefs.edit().putInt(COPYABLE_TEXT_COLOR, value).apply()

    // thread view: message body
    var threadSnippetTextSize: Int
        get() = prefs.getInt(THREAD_SNIPPET_TEXT_SIZE, 12)
        set(value) = prefs.edit().putInt(THREAD_SNIPPET_TEXT_SIZE, value).apply()

    var threadSnippetTextColor: Int
        get() = prefs.getInt(THREAD_SNIPPET_TEXT_COLOR, 0xff555555.toInt())
        set(value) = prefs.edit().putInt(THREAD_SNIPPET_TEXT_COLOR, value).apply()

    // thread view: date / sender info
    var threadInfoTextSize: Int
        get() = prefs.getInt(THREAD_INFO_TEXT_SIZE, 10)
        set(value) = prefs.edit().putInt(THREAD_INFO_TEXT_SIZE, value).apply()

    var threadInfoTextColor: Int
        get() = prefs.getInt(THREAD_INFO_TEXT_COLOR, 0xff999999.toInt())
        set(value) = prefs.edit().putInt(THREAD_INFO_TEXT_COLOR, value).apply()

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
