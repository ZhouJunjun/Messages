package org.fossify.messages.helpers

import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.math.abs
import kotlin.random.Random

const val THREAD_ID = "thread_id"
const val THREAD_TITLE = "thread_title"
const val THREAD_TEXT = "thread_text"
const val THREAD_NUMBER = "thread_number"
const val THREAD_ATTACHMENT_URI = "thread_attachment_uri"
const val THREAD_ATTACHMENT_URIS = "thread_attachment_uris"
const val SEARCHED_MESSAGE_ID = "searched_message_id"
const val USE_SIM_ID_PREFIX = "use_sim_id_"
const val NOTIFICATION_CHANNEL_ID = "fossify_messages"
const val SHOW_CHARACTER_COUNTER = "show_character_counter"
const val USE_SIMPLE_CHARACTERS = "use_simple_characters"
const val SEND_ON_ENTER = "send_on_enter"
const val LOCK_SCREEN_VISIBILITY = "lock_screen_visibility"
const val ENABLE_DELIVERY_REPORTS = "enable_delivery_reports"
const val SEND_LONG_MESSAGE_MMS = "send_long_message_mms"
const val SEND_GROUP_MESSAGE_MMS = "send_group_message_mms"
const val MMS_FILE_SIZE_LIMIT = "mms_file_size_limit"
const val PINNED_CONVERSATIONS = "pinned_conversations"
const val BLOCKED_KEYWORDS = "blocked_keywords"
const val LAST_BLOCKED_KEYWORD_EXPORT_PATH = "last_blocked_keyword_export_path"
const val EXPORT_SMS = "export_sms"
const val EXPORT_MMS = "export_mms"
const val JSON_FILE_EXTENSION = ".json"
const val JSON_MIME_TYPE = "application/json"
const val XML_MIME_TYPE = "text/xml"
const val TXT_MIME_TYPE = "text/plain"
const val IMPORT_SMS = "import_sms"
const val IMPORT_MMS = "import_mms"
const val WAS_DB_CLEARED = "was_db_cleared_4"
const val EXTRA_VCARD_URI = "vcard"
const val SCHEDULED_MESSAGE_ID = "scheduled_message_id"
const val SOFT_KEYBOARD_HEIGHT = "soft_keyboard_height"
const val IS_MMS = "is_mms"
const val MESSAGE_ID = "message_id"
const val CUSTOM_NOTIFICATIONS = "custom_notifications"
const val IS_LAUNCHED_FROM_SHORTCUT = "is_launched_from_shortcut"

// custom appearance
const val HOME_TITLE_TEXT_SIZE = "home_title_text_size"
const val HOME_TITLE_TEXT_COLOR = "home_title_text_color"
const val HOME_SNIPPET_TEXT_SIZE = "home_snippet_text_size"
const val HOME_SNIPPET_TEXT_COLOR = "home_snippet_text_color"
const val HOME_INFO_TEXT_SIZE = "home_info_text_size"
const val HOME_INFO_TEXT_COLOR = "home_info_text_color"
const val COPYABLE_TEXT_SIZE = "copyable_text_size"
const val COPYABLE_TEXT_COLOR = "copyable_text_color"
const val THREAD_SNIPPET_TEXT_SIZE = "thread_snippet_text_size"
const val THREAD_SNIPPET_TEXT_COLOR = "thread_snippet_text_color"
const val THREAD_INFO_TEXT_SIZE = "thread_info_text_size"
const val THREAD_INFO_TEXT_COLOR = "thread_info_text_color"

private const val PATH = "org.fossify.org.fossify.messages.action."
const val MARK_AS_READ = PATH + "mark_as_read"
const val REPLY = PATH + "reply"

// view types for the thread list view
const val THREAD_RECEIVED_MESSAGE = 2
const val THREAD_SENT_MESSAGE = 3
const val THREAD_SENT_MESSAGE_ERROR = 4
const val THREAD_SENT_MESSAGE_SENT = 5
const val THREAD_SENT_MESSAGE_SENDING = 6
const val THREAD_TYPE_BITS = 3
const val THREAD_KEY_BITS = Long.SIZE_BITS - THREAD_TYPE_BITS
const val THREAD_TYPE_SHIFT = THREAD_KEY_BITS
const val THREAD_KEY_MASK = (1L shl THREAD_KEY_BITS) - 1

// view types for attachment list
const val ATTACHMENT_DOCUMENT = 7
const val ATTACHMENT_MEDIA = 8
const val ATTACHMENT_VCARD = 9
const val ATTACHMENT_AUDIO = 10

// lock screen visibility constants
const val LOCK_SCREEN_SENDER_MESSAGE = 1
const val LOCK_SCREEN_SENDER = 2
const val LOCK_SCREEN_NOTHING = 3

const val FILE_SIZE_NONE = -1L
const val FILE_SIZE_100_KB = 102_400L
const val FILE_SIZE_200_KB = 204_800L
const val FILE_SIZE_300_KB = 307_200L
const val FILE_SIZE_600_KB = 614_400L
const val FILE_SIZE_1_MB = 1_048_576L
const val FILE_SIZE_2_MB = 2_097_152L

const val MESSAGES_LIMIT = 50
const val MAX_MESSAGE_LENGTH = 5000

// intent launch request codes
const val PICK_PHOTO_INTENT = 42
const val PICK_VIDEO_INTENT = 49
const val PICK_SAVE_FILE_INTENT = 43
const val CAPTURE_PHOTO_INTENT = 44
const val CAPTURE_VIDEO_INTENT = 45
const val CAPTURE_AUDIO_INTENT = 46
const val PICK_DOCUMENT_INTENT = 47
const val PICK_CONTACT_INTENT = 48
const val PICK_SAVE_DIR_INTENT = 50

const val BLOCKED_KEYWORDS_EXPORT_DELIMITER = ","
const val BLOCKED_KEYWORDS_EXPORT_EXTENSION = ".txt"

fun refreshMessages() {
    EventBus.getDefault().post(Events.RefreshMessages())
}

fun refreshConversations() {
    EventBus.getDefault().post(Events.RefreshConversations())
}

/** Not to be used with real messages persisted in the telephony db. This is for internal use only (e.g. scheduled messages, notification ids etc). */
fun generateRandomId(length: Int = 9): Long {
    val millis = DateTime.now(DateTimeZone.UTC).millis
    val random = abs(Random(millis).nextLong())
    return random.toString().takeLast(length).toLong()
}

fun generateStableId(type: Int, key: Long): Long {
    require(type in 0 until (1 shl THREAD_TYPE_BITS))
    return (type.toLong() shl THREAD_TYPE_SHIFT) or (key and THREAD_KEY_MASK)
}
