package org.fossify.messages

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.appcompat.app.AppCompatDelegate
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.rescheduleAllScheduledMessages
import org.fossify.messages.helpers.MessagingCache
import org.fossify.messages.helpers.THEME_MODE_DARK
import org.fossify.messages.helpers.THEME_MODE_LIGHT

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()

        // This build is a legitimate fork shipped under its own application id, but Commons'
        // `checkAppSideloading()` decides it is a "fake version" (it probes for a drawable that the
        // resource shrinker drops) and pops a "download the original app" dialog. Cache the
        // "not sideloaded" answer before any activity reads it so the dialog never shows up.
        config.appSideloadingStatus = 2

        // 1.0.9 spec: the appearance screen owns the theme, so the app stops simply following the
        // device. Also re-applies the re-defined default text sizes for installs that already have
        // the old defaults stored.
        config.migrateAppearanceDefaults()
        applyThemeMode()

        if (hasPermission(PERMISSION_READ_CONTACTS)) {
            listOf(
                ContactsContract.Contacts.CONTENT_URI,
                ContactsContract.Data.CONTENT_URI,
                ContactsContract.DisplayPhoto.CONTENT_URI
            ).forEach {
                try {
                    contentResolver.registerContentObserver(it, true, contactsObserver)
                } catch (_: Exception) {
                }
            }
        }

        ensureBackgroundThread {
            rescheduleAllScheduledMessages()
        }
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            MessagingCache.namePhoto.evictAll()
            MessagingCache.participantsCache.evictAll()
        }
    }

    /**
     * Pushes the configured theme onto the whole process.
     *
     * On Android 12+ commons builds its theme from the *system* day/night state, which is why the
     * app used to look dark on a dark device without the user ever asking for it. AppCompat's night
     * mode rewrites exactly that state for the app, so forcing it here is what makes
     * light / dark / follow-system work - and it is also what makes the appearance screen's two
     * colour sets know which half to hand out (see `Config.isDarkAppearance`).
     *
     * Called from [onCreate] and again whenever the appearance screen changes the mode; the second
     * call makes AppCompat recreate the running activities, which is how the change shows up
     * immediately.
     */
    fun applyThemeMode() {
        val nightMode = when (config.themeMode) {
            THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
